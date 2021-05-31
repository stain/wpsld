/*

 * This Java source file was generated by the Gradle 'init' task.
 */
package no.s11.wpsld.api;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;

import no.s11.wpsld.WPSLD;
import no.s11.wpsld.WPSLDPath;
import no.s11.wpsld.impl.WPSLDImpl;

public class WPSLDTest {
    @Test public void testSomeLibraryMethod() throws IOException {
        WPSLD wpsld = new WPSLDImpl();
        WPSLDPath path = wpsld.newRoot();
        assertTrue("New root should be a directory", 
        		Files.isDirectory(path.getPath()));        
    }
}
