import os
from argparse import ArgumentParser

script_dir = os.path.dirname(os.path.realpath(__file__))
slicer4j_dir = os.path.dirname(script_dir)
logger_jar = os.path.dirname(slicer4j_dir) + "/DynamicSlicingCore/DynamicSlicingLoggingClasses/DynamicSlicingLogger.jar"


def is_int(val) -> bool:
    try:
        int(val)
        return True
    except ValueError:
        return False


def main():
    options = parse()

    backward_criterion = options["backward_criterion"]

    jar_file = options["jar_file"]
    if not os.path.isfile(jar_file):
        #print("Jar file does not exist!")
        return
    jar_file = os.path.abspath(jar_file)
    print('jar_file is: '+ jar_file)

    out_dir = options["out_dir"]
    if not os.path.isdir(out_dir):
        print("{} does not exist, creating it", out_dir)
        os.makedirs(out_dir)
    out_dir = os.path.abspath(out_dir)
    print('out_dir is: ' + out_dir)

    dependencies = options["dependencies"]
    if dependencies and not os.path.isdir(dependencies):
        print("Dependencies directory doesn't exist")
        return
    if dependencies:
        dependencies = os.path.abspath(options["dependencies"])

    test_class = options["test_class"]
    test_method = options["test_method"]
    main_class_args = options["main_class_args"]
    framework_models = options["framework_models"]

    extra_options = ""
    if options["debug"]:
        extra_options += "-d "
    if options["data_only"]:
        extra_options += "-data "
    if options["ctrl_only"]:
        extra_options += "-ctrl "
    if options["once"]:
        extra_options += "-once "
    if options["data_only"] and options["ctrl_only"]:
        #print("Conflicting arguments: data-only and control-only!")
        return
    if (not main_class_args) and (not test_class or not test_method):
        #print("Must provide either main class name and arguments or test class and test method")
        return
    if framework_models:
        extra_options += "-f " + framework_models

    instrumented_jar = instrument(jar_file=jar_file, out_dir=out_dir)
    run(instrumented_jar, dependencies, out_dir, test_class, test_method, main_class_args)
    log_file, slice_graph = dynamic_slice(jar_file=jar_file, out_dir=out_dir, backward_criterion=backward_criterion,
                                          extra_options=extra_options)

    print("Slice source code lines: {}/slice.log".format(out_dir))
    print("Raw slice: {}/raw-slice.log".format(out_dir))
    print("Slice graph: {}".format(slice_graph))
    print("Slice with dependencies: {}/slice-dependencies.log".format(out_dir))


def instrument(jar_file: str, out_dir: str) -> str:
    instr_file = "instr-debug.log"
    print("Instrumenting the JAR " + jar_file, flush=True)
    instr_cmd = "java -Xmx64g -cp \"{}/Slicer4J/target/slicer4j-jar-with-dependencies.jar:{}/Slicer4J/target/lib/*\" ca.ubc.ece.resess.slicer.dynamic.slicer4j.Slicer -m i -j {} -o {}/ -sl {}/static_log.log -lc {} > {}/{} 2>&1".format(slicer4j_dir, slicer4j_dir, jar_file, out_dir, out_dir, logger_jar, out_dir, instr_file)
    #print ('instr_cmd is: ' + instr_cmd)
    os.system(instr_cmd)
    instrumented_jar = os.path.basename(jar_file).replace(".jar", "_i.jar")
    out_instrumented_jar = out_dir + os.sep + instrumented_jar
    print('instrumented jar is: ' + out_instrumented_jar)
    return out_instrumented_jar


def run(instrumented_jar, dependencies, out_dir, test_class, test_method, main_class_args):
    #print("Running the instrumented JAR", flush=True)
    if main_class_args is None:
        
        # UNCOMMENT THIS IF USING JUNIT 5
        #cmd = "java -Xmx64g -cp \"{}/JUnitTestRunner.jar:{}/junit-jupiter-engine-5.7.2.jar:{}/junit5-4.2.9.jar:{}/junit-jupiter-api-5.5.2.jar:{}/junit-platform-commons-1.8.0-M1.jar:{}/junit-platform-engine-1.8.0-M1.jar:{}/junit-platform-launcher-1.8.0-M1.jar:{}/opentest4j-1.2.0.jar:{}/apiguardian-api-1.1.1.jar:{}:{}/*\" JUnitTestRunner {}#{} > {}/trace_full.log".format(script_dir,script_dir,script_dir,script_dir,script_dir,script_dir,script_dir,script_dir,script_dir,instrumented_jar,dependencies,test_class,test_method,out_dir)
        
        # UNCOMMENT THIS IF USING JUNIT 4
        cmd = "java -Xmx64g -cp \"{}/SingleJUnitTestRunner.jar:{}/junit-4.8.2.jar:{}:{}/*\" SingleJUnitTestRunner {}#{} > {}/trace_full.log".format(script_dir,script_dir,instrumented_jar,dependencies,test_class,test_method,out_dir)
        
        print('command to run test: ' + cmd)
    else:
        if main_class_args.startswith("\"") and main_class_args.endswith("\""):
            main_class_args = main_class_args[1:-1]
        cmd = "java -Xmx64g -cp \"{}:{}/*\" {} > {}/trace_full.log".format(instrumented_jar, dependencies, main_class_args, out_dir)
    
    #print(f"Running instrumented JAR", flush=True)
    #print(f"------------------------------------")
    os.system(cmd)
    #print(f"------------------------------------")
    os.system("cat {}/trace_full.log | grep \"SLICING\" > {}/trace.log".format(out_dir,out_dir))
    trace = list()
    with open("{}/trace.log".format(out_dir), 'r') as f:
        for line in f:
            if "FIELD" in line:
                del trace[-1]
            trace.append(line.rstrip())

    with open("{}/trace.log".format(out_dir), 'w') as f:
        for t in trace:
            f.write(t + "\n")


def dynamic_slice(jar_file=None, out_dir=None, backward_criterion=None, variables=None, extra_options=""):
    slice_file = "slice-file.log"
    graph_file = "graph-debug.log"
    #if variables:
        #print(f"Slicing from line {backward_criterion} with variables {variables}", flush=True)
    #else:
        #print(f"Slicing from line {backward_criterion}", flush=True)
    graph_cmd = "java -Xmx64g -cp \"{}/Slicer4J/target/slicer4j-jar-with-dependencies.jar:{}/Slicer4J/target/lib/*\" ca.ubc.ece.resess.slicer.dynamic.slicer4j.Slicer -m g -j {} -t {}/trace.log -o {}/ -sl {}/static_log.log -sd {}/models/summariesManual -tw {}/models/EasyTaintWrapperSource.txt > {}/{} 2>&1".format(slicer4j_dir,slicer4j_dir,jar_file,out_dir,out_dir,out_dir,slicer4j_dir,slicer4j_dir,out_dir,graph_file)
    os.system(graph_cmd)

    clazz, lineno = backward_criterion.split(":")
    # clazz = clazz.rsplit(".", 1)[0]
    check_str = ":LINENO:{}:FILE:{}".format(lineno,clazz)
    slice_line = ""
    with open("{}/trace.log_icdg.log".format(out_dir), 'r') as f:
        for line in f:
            if check_str in line:
                if slice_line:
                    slice_line = slice_line + "-"
                slice_line = slice_line + line.rstrip().split(", ")[0]

    if variables:
        extra_options += "-sv " + str(variables)

    slice_cmd = "java -Xmx64g -cp \"{}/Slicer4J/target/slicer4j-jar-with-dependencies.jar:{}/Slicer4J/target/lib/*\" ca.ubc.ece.resess.slicer.dynamic.slicer4j.Slicer -m s -j {} -t {}/trace.log -o {}/ -sl {}/static_log.log -sd {}/models/summariesManual -tw {}/models/EasyTaintWrapperSource.txt -sp {} {} > {}/{} 2>&1".format(slicer4j_dir,slicer4j_dir,jar_file,out_dir,out_dir,out_dir,slicer4j_dir,slicer4j_dir,slice_line,extra_options,out_dir,slice_file)
    os.system(slice_cmd)
    arr = [x for x in os.listdir(out_dir) if x.startswith("result_md")]
    for a in arr:
        os.system("rm {}/{a}".format(out_dir, a))
    return "{}/{}".format(out_dir,slice_file), "{}/slice-graph.pdf".format(out_dir)


def parse():
    parser = ArgumentParser()
    parser.add_argument("-j", "--jar_file", dest="jar_file",
                        help="JAR file", metavar="path/to/jar", required=True)
    parser.add_argument("-o", "--out_dir", dest="out_dir",
                        help="Output folder", metavar="path/to/out/folder", required=True)
    parser.add_argument("-b", "--backward-criterion", dest="backward_criterion",
                        help="Backward criterion (line number)", metavar="line to slice backward from", required=True)
    parser.add_argument("-v", "--variables", dest="variables",
                        help="Variables to slice from, list of - separated names", metavar="variables to slice from",
                        required=False)
    parser.add_argument("-mod", "--models", dest="framework_models",
                        help="Folder containing user-defined method models", metavar="user defined framework models",
                        required=False)
    parser.add_argument("-debug", "--debug", dest="debug",
                        help="Enable debug", action='store_true', required=False)
    parser.add_argument("-d", "--data", dest="data_only",
                        help="Slice with data-flow dependencies only", action='store_true', required=False)
    parser.add_argument("-c", "--control", dest="ctrl_only",
                        help="Slice with control dependencies only", action='store_true', required=False)
    parser.add_argument("-once", "--once", dest="once",
                        help="Slice for first statement only then stop", action='store_true', required=False)
    parser.add_argument("-tc", "--test-class", dest="test_class",
                        help="Test class to run", metavar="name", required=False)
    parser.add_argument("-tm", "--test-method", dest="test_method",
                        help="Test method to run", metavar="name", required=False)
    parser.add_argument("-m", "--main-class-args", dest="main_class_args",
                        help="Main class to run with arguments", metavar="name", required=False)
    parser.add_argument("-dep", "--dependencies", dest="dependencies",
                        help="JAR dependencies", metavar="path", required=False)
    args = parser.parse_args()
    return {
        "jar_file": args.jar_file, "out_dir": args.out_dir, "backward_criterion": args.backward_criterion,
        "variables": args.variables, "data_only": args.data_only, "ctrl_only": args.ctrl_only,
        "test_class": args.test_class, "test_method": args.test_method, "main_class_args": args.main_class_args,
        "dependencies": args.dependencies, "framework_models": args.framework_models, "debug": args.debug, "once": args.once
    }


if __name__ == "__main__":
    print('script_dir: ' + script_dir )
    print('slicer4j_dir: ' + slicer4j_dir)
    print('logger_jar: ' + logger_jar)

    main()
