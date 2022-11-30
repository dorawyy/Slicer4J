import static org.junit.platform.engine.discovery.ClassNameFilter.includeClassNamePatterns;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryListener;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
//import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener;

public class JUnitTestRunner {
    public static void main(String [] strArr) throws ClassNotFoundException {
        String[] split = strArr[0].split("#");
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(
                //selectPackage("com.example.mytests"),
                selectClass(Class.forName(split[0]))
            )
            //.filters(
            //    includeClassNamePatterns(".*Tests")
            // )
            .build();

        SummaryGeneratingListener listener = new SummaryGeneratingListener();

        try (LauncherSession session = LauncherFactory.openSession()) {
            Launcher launcher = session.getLauncher();
            // Register a listener of your choice
            launcher.registerTestExecutionListeners(listener);
            // Discover tests and build a test plan
            //*TestPlan testPlan = launcher.discover(request);
            // Execute test plan
            //*launcher.execute(testPlan);
            // Alternatively, execute the request directly
            launcher.execute(request);
        }
        
        TestExecutionSummary summary = listener.getSummary();
        
        int i = 0;
        if(summary.getTestsFailedCount() == 0 && summary.getTestsSucceededCount() == 0) {
            System.out.println("No tests were run");
        } else if (summary.getTestsFailedCount() > 0) {
            System.out.println("Test fail");
            i = 1;
        } else {
            System.out.println("Test pass");
        }
        System.exit(i);
    }
}
