package ca.ubc.ece.resess.slicer.dynamic.slicer4j;

import ca.ubc.ece.resess.slicer.dynamic.slicer4j.Slicer;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.io.IOException;  // Import the IOException class to handle errors

import soot.*;
import soot.options.Options;
import soot.util.Chain;

public class GetStructuralDependencies {

    private static final Path root = Paths.get(".", "Slicer4J").normalize().toAbsolutePath();
    private static final Path slicerPath = Paths.get(root.toString(), "scripts");
    private static final Path outDir = Paths.get(slicerPath.toString(), "output");
    private static final Path sliceLogger = Paths.get(root.getParent().toString(),   File.separator + "DynamicSlicingCore" + File.separator + "DynamicSlicingLoggingClasses" + File.separator + "DynamicSlicingLogger.jar");


    public GetStructuralDependencies() {}


    public static void main(String[] args) throws Exception {
        if(args.length != 3) {
            System.out.println("Usage: java ca.ubc.ece.resess.slicer.dynamic.slicer4j.getStaticGraph <path-to-app-jar> <output-static-graph-file-name> <s|d|both>");
            System.exit(0);
        }

        // read input 
        String appJar = Paths.get(args[0]).toString(); // path to application jar 
        String outFile = args[1]; // static dependency output file
        String analysisType = args[2]; // flag if to extract static dependency, dynamic dependency, or both
        int analysisFlag = -1; 
        if (analysisType.toLowerCase().equals("s") || analysisType.toLowerCase().equals("static")) {
            analysisFlag = 1;
        }
        else if (analysisType.toLowerCase().equals("d") || analysisType.toLowerCase().equals("dynamic")) {
            analysisFlag = 2;
        }
        else if (analysisType.toLowerCase().equals("b") || analysisType.toLowerCase().equals("both")) {
            analysisFlag = 3;
        }
        else {
            System.out.println("Unrecognizable analysis option. The only valid options are: 's' for extracting static dependency, 'd' for generate instrumented app for further use, 'b' for both exacting static dependency and generating instrumented app for further dynamic dependency extraction.");
            System.exit(0);
        }

        // setup slicer4j
        Slicer slicer = setupSlicing(root, appJar, outDir, sliceLogger);
        slicer.prepare();

        if (analysisFlag == 1 || analysisFlag == 3){
            // generate and output static structural dependnecy 
            HashMap<String, HashMap<String, Integer>> staticGraph = analyzeStaticDependencies();  
            writeStaticDependencies(outFile, staticGraph);
        }
        if(analysisFlag == 2 || analysisFlag == 3){
            // dynamic dependency, produce instrumented application 
            slicer.instrument();
            System.out.println("the instrumented application is generated!");
        }
    }

    public static Slicer setupSlicing(Path root, String jarPath, Path outDir, Path sliceLogger) {
        Slicer slicer = new Slicer();
        slicer.setPathJar(jarPath);
        slicer.setOutDir(outDir.toString()); // application dir 
        slicer.setLoggerJar(sliceLogger.toString());

        slicer.setFileToParse(outDir + File.separator + "trace.log");
        slicer.setStubDroidPath(root.toString() + File.separator + "models" + File.separator + "summariesManual");
        slicer.setTaintWrapperPath(root.toString() + File.separator + "models" + File.separator + "EasyTaintWrapperSource.txt");
        
        slicer.setDebug(true);

        return slicer;
    }

    

    /***
     * write the input static graph to the given outFile
     * @param outFile
     * @param staticGraph
     */
    public static void writeStaticDependencies(String outFile, HashMap<String, HashMap<String, Integer>> staticGraph){
        System.out.printf("Start writing Static Graph to file %s...\n", outFile);
        try {
            FileWriter myWriter = new FileWriter(outFile);
            for(String callerClass : staticGraph.keySet()) {
                for(String calleeClass : staticGraph.get(callerClass).keySet()) {
                    myWriter.write(callerClass + "," + calleeClass + "," + staticGraph.get(callerClass).get(calleeClass).toString() + "\n");
                }
            }
            myWriter.close();
        } catch (IOException e) {
            System.out.println("An error occurred when writing to the static dependency graph file.");
            e.printStackTrace();
        }

        System.out.println("Finished writing Static Graph...\n");
    }


    public static HashMap<String, HashMap<String, Integer>> analyzeStaticDependencies() {
        Chain<SootClass> chain = Scene.v().getApplicationClasses();
        return getStaticDependencies(chain);
    }

    protected static HashMap<String, HashMap<String, Integer>> getStaticDependencies(Chain<SootClass> chain) {
        Iterator<SootClass> iterator = chain.snapshotIterator();
        HashMap<String, HashMap<String, Integer>> staticDependencies = new HashMap<String, HashMap<String, Integer>>();
        while (iterator.hasNext()) {
            SootClass sc = iterator.next();
            sc.setApplicationClass();
            staticDependencies.put(sc.getName(), new HashMap<String, Integer>());

            // Data Dependency: Here are the hierarchy dependencies
            String superClass = sc.getSuperclass().getName();
            staticDependencies.get(sc.getName()).put(superClass, 1);

            // Data Dependency: Class Fields
            for(SootField sf : sc.getFields()) {
                // Check if the type is part of the classes.
                String field = sf.getType().toString();
                if(staticDependencies.get(sc.getName()).containsKey(field)) {
                    int oldCall = staticDependencies.get(sc.getName()).get(field);
                    staticDependencies.get(sc.getName()).replace(field, oldCall + 1);
                } else {
                    staticDependencies.get(sc.getName()).put(field, 1);
                }
            }

            List<SootMethod> methods = sc.getMethods();
            for (SootMethod mt : methods) {
                if(mt.getExceptions() != null) {
                    for(SootClass exceptionClass : mt.getExceptions()) {
                        String exception = exceptionClass.getName();
                        if(staticDependencies.get(sc.getName()).containsKey(exception)) {
                            int oldCall = staticDependencies.get(sc.getName()).get(exception);
                            staticDependencies.get(sc.getName()).replace(exception, oldCall + 1);
                        } else {
                            staticDependencies.get(sc.getName()).put(exception, 1);
                        }
                    }
                }
                // Data Dependency: Method Parameters
                for(Type parameterType : mt.getParameterTypes()) {
                    String parameter = parameterType.toString();
                    if(staticDependencies.get(sc.getName()).containsKey(parameter)) {
                        int oldCall = staticDependencies.get(sc.getName()).get(parameter);
                        staticDependencies.get(sc.getName()).replace(parameter, oldCall + 1);
                    } else {
                        staticDependencies.get(sc.getName()).put(parameter, 1);
                    }
                }
                // Data Dependency: Return type
                String returnType = mt.getReturnType().toString();
                if(staticDependencies.get(sc.getName()).containsKey(returnType)) {
                    int oldCall = staticDependencies.get(sc.getName()).get(returnType);
                    staticDependencies.get(sc.getName()).replace(returnType, oldCall + 1);
                } else {
                    staticDependencies.get(sc.getName()).put(returnType, 1);
                }

                try {
                    if(mt.getActiveBody()==null) {
                        continue;
                    }
                } catch(Exception ex) {
                    continue;
                }
                Body b;
                try {
                    b = mt.getActiveBody();
                } catch (Exception ex) {
                    continue;
                }
                soot.PatchingChain<Unit> units = b.getUnits();
                for( Unit unit : units) {
                    List<ValueBox> valBoxes = unit.getDefBoxes();
                    for(ValueBox val : valBoxes) {
                        String methodCall = val.getValue().getType().toString();
                        if(staticDependencies.get(sc.getName()).containsKey(methodCall)) {
                            int oldCall = staticDependencies.get(sc.getName()).get(methodCall);
                            staticDependencies.get(sc.getName()).replace(methodCall, oldCall + 1);
                        } else {
                            staticDependencies.get(sc.getName()).put(methodCall, 1);
                        }
                    }
                }
            }
        }
        return staticDependencies;
    }

}
