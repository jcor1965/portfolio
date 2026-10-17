package name.abuchen.portfolio.ui.views.trades;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Spinner;
import org.junit.Test;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;

@SuppressWarnings("nls")
public class FifoExportDialogTest
{
    private static void controls(Composite composite, ArrayList<Control> result)
    {
        for (var control : composite.getChildren())
        {
            result.add(control);
            if (control instanceof Composite child)
                controls(child, result);
        }
    }

    @Test
    public void selectsTrading212AndCurrentYearWithoutOpeningExport()
    {
        var display = Display.getDefault();
        display.syncExec(() -> {
            var client = new Client();
            client.addPortfolio(new Portfolio("Other institution"));
            client.addPortfolio(new Portfolio("Trading 212"));
            var dialog = new FifoExportDialog(null, client);
            dialog.create();
            try
            {
                var all = new ArrayList<Control>();
                controls(dialog.getShell(), all);
                var combo = all.stream().filter(Combo.class::isInstance).map(Combo.class::cast).findFirst().orElseThrow();
                var spinner = all.stream().filter(Spinner.class::isInstance).map(Spinner.class::cast).findFirst().orElseThrow();
                assertEquals("Trading 212", combo.getText());
                assertEquals(LocalDate.now().getYear(), spinner.getSelection());
                assertEquals(2, combo.getItemCount());
            }
            finally
            {
                dialog.close();
            }
        });
    }

    @Test
    public void packagedApplicationModelContainsImportCommandAndHandler() throws Exception
    {
        var bootstrap = Platform.getBundle("name.abuchen.portfolio.bootstrap");
        assertNotNull(bootstrap);
        var resource = bootstrap.getEntry("Application.e4xmi");
        assertNotNull(resource);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var input = resource.openStream())
        {
            var document = factory.newDocumentBuilder().parse(input);
            var xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
            assertEquals("1", xpath.evaluate("count(//*[@elementId='personal.import.trading212'])", document));
            assertEquals("1", xpath.evaluate("count(//*[@elementId='personal.import.trading212.command'])", document));
            var uri = xpath.evaluate("string(//*[@elementId='personal.import.trading212.handler']/@contributionURI)", document);
            assertTrue(uri.endsWith("name.abuchen.portfolio.ui.handlers.ImportTrading212Handler"));
            assertNotNull(Class.forName("name.abuchen.portfolio.ui.handlers.ImportTrading212Handler"));
        }
    }
}
