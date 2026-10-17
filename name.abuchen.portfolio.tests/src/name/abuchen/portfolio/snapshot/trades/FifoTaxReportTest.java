package name.abuchen.portfolio.snapshot.trades;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.csv.CSVFormat;
import org.junit.Assume;
import org.junit.Test;

import name.abuchen.portfolio.junit.TestCurrencyConverter;
import name.abuchen.portfolio.model.AttributeType;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.ClientFactory;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Transaction.Unit;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;

@SuppressWarnings("nls")
public class FifoTaxReportTest
{
    private final Client client = new Client();
    private final Portfolio portfolio = new Portfolio("Test institution");
    private final Security security = new Security("Example <ETF> & test", "EUR");

    public FifoTaxReportTest()
    {
        security.setIsin("IE00077FRP95");
        client.addSecurity(security);
        client.addPortfolio(portfolio);
    }

    private PortfolioTransaction transaction(PortfolioTransaction.Type type, String date, long shares, long cents)
    {
        var t = new PortfolioTransaction(type);
        t.setDateTime(LocalDateTime.parse(date));
        t.setCurrencyCode("EUR");
        t.setSecurity(security);
        t.setShares(shares);
        t.setAmount(cents);
        portfolio.addTransaction(t);
        return t;
    }

    private void example()
    {
        for (int month = 1; month <= 4; month++)
            transaction(PortfolioTransaction.Type.BUY, "2026-0" + month + "-01T10:00:00", Values.Share.factorize(10), month * 10000);
        transaction(PortfolioTransaction.Type.SELL, "2026-05-01T10:00:00", Values.Share.factorize(25), 100000);
    }

    @Test
    public void splitsSaleAcrossOldestThreePurchases()
    {
        example();
        var report = FifoTaxReport.generate(client, portfolio, 2026);
        assertEquals(3, report.lots().size());
        assertEquals(List.of(10L, 10L, 5L), report.lots().stream().map(l -> l.shares() / Values.Share.factor()).toList());
        assertEquals(List.of(10000L, 20000L, 15000L), report.lots().stream().map(l -> l.cost()).toList());
        assertEquals(55000, report.sales().getFirst().gain());
        assertEquals(5, portfolio.getTransactions().size());
    }

    @Test
    public void consumesEarlierYearsAndConservesCents()
    {
        transaction(PortfolioTransaction.Type.BUY, "2024-01-01T00:00:00", 3, 100);
        transaction(PortfolioTransaction.Type.SELL, "2025-01-01T00:00:00", 1, 100);
        transaction(PortfolioTransaction.Type.SELL, "2026-01-01T00:00:00", 1, 100);
        transaction(PortfolioTransaction.Type.SELL, "2026-02-01T00:00:00", 1, 100);
        var report = FifoTaxReport.generate(client, portfolio, 2026);
        assertEquals(List.of(34L, 33L), report.lots().stream().map(l -> l.cost()).toList());
        assertEquals(2, report.sales().size());
    }

    @Test
    public void preservesFractionalQuantitiesAndSaleRemainder()
    {
        transaction(PortfolioTransaction.Type.BUY, "2026-01-01T00:00:00", 12345678, 5);
        transaction(PortfolioTransaction.Type.BUY, "2026-02-01T00:00:00", 12345678, 5);
        transaction(PortfolioTransaction.Type.SELL, "2026-03-01T00:00:00", 24691356, 11);
        var report = FifoTaxReport.generate(client, portfolio, 2026);
        assertEquals(List.of(6L, 5L), report.lots().stream().map(l -> l.proceeds()).toList());
        assertEquals(24691356L, report.sales().getFirst().shares());
    }

    @Test
    public void rejectsMissingHistoryAndDoesNotUseOtherInstitutions()
    {
        var other = new Portfolio("Other institution");
        client.addPortfolio(other);
        var buy = transaction(PortfolioTransaction.Type.BUY, "2026-01-01T00:00:00", 1, 1);
        portfolio.getTransactions().remove(buy);
        other.addTransaction(buy);
        transaction(PortfolioTransaction.Type.SELL, "2026-02-01T00:00:00", 1, 2);
        assertThrows(IllegalArgumentException.class, () -> FifoTaxReport.generate(client, portfolio, 2026));
    }

    @Test
    public void rejectsUnsupportedTypesFxChargesAndDuplicateIds()
    {
        var buy = transaction(PortfolioTransaction.Type.BUY, "2026-01-01T00:00:00", 1, 1);
        buy.setCurrencyCode("USD");
        assertThrows(IllegalArgumentException.class, () -> FifoTaxReport.generate(client, portfolio, 2026));
        buy.setCurrencyCode("EUR");
        buy.setType(PortfolioTransaction.Type.DELIVERY_INBOUND);
        assertThrows(IllegalArgumentException.class, () -> FifoTaxReport.generate(client, portfolio, 2026));
        buy.setType(PortfolioTransaction.Type.BUY);
        buy.addUnit(new Unit(Unit.Type.FEE, Money.of("EUR", 1)));
        assertThrows(IllegalArgumentException.class, () -> FifoTaxReport.generate(client, portfolio, 2026));
        portfolio.getTransactions().clear();
        var first = transaction(PortfolioTransaction.Type.BUY, "2026-01-01T00:00:00", 1, 1);
        var second = transaction(PortfolioTransaction.Type.BUY, "2026-02-01T00:00:00", 1, 1);
        first.setNote("Market buy | Trading 212 ID: EX1");
        second.setNote(first.getNote());
        assertThrows(IllegalArgumentException.class, () -> FifoTaxReport.generate(client, portfolio, 2026));
    }

    @Test
    public void ignoresFutureMovementsAndReadsCustomCountryAttributes()
    {
        example();
        transaction(PortfolioTransaction.Type.SELL, "2027-01-01T00:00:00", 999999999999L, 1);
        var country = new AttributeType("test-country");
        country.setName("País da fonte — Nome");
        client.getSettings().addAttributeType(country);
        security.getAttributes().put(country, "Irlanda");
        var code = new AttributeType("test-code");
        code.setName("País da fonte — AT");
        client.getSettings().addAttributeType(code);
        security.getAttributes().put(code, "372");
        var report = FifoTaxReport.generate(client, portfolio, 2026);
        assertEquals(1, report.sales().size());
        assertEquals("372", report.sales().getFirst().countryCode());
        assertEquals("Irlanda", report.lots().getFirst().country());
    }

    @Test
    public void reconcilesWithNativeCombinedTradeCollector() throws Exception
    {
        example();
        var report = FifoTaxReport.generate(client, portfolio, 2026);
        var nativeTrade = new TradeCollector(client, new TestCurrencyConverter()).collect(security).stream()
                        .filter(Trade::isClosed).findFirst().orElseThrow();
        assertEquals(nativeTrade.getEntryValue().getAmount(), report.sales().getFirst().cost());
        assertEquals(nativeTrade.getExitValue().getAmount(), report.sales().getFirst().proceeds());
    }

    @Test
    public void writesValidExcelWithTypedDatesAndEscapedLiteralText() throws Exception
    {
        example();
        var target = Files.createTempFile("fifo-test-", ".xlsx");
        try
        {
            FifoXlsxWriter.write(FifoTaxReport.generate(client, portfolio, 2026), target);
            try (var zip = new ZipFile(target.toFile()))
            {
                var factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                for (var entries = zip.entries(); entries.hasMoreElements();)
                {
                    var entry = entries.nextElement();
                    try (var input = zip.getInputStream(entry))
                    {
                        factory.newDocumentBuilder().parse(input);
                    }
                }
                var sheet = new String(zip.getInputStream(zip.getEntry("xl/worksheets/sheet2.xml")).readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(sheet.contains("Example &lt;ETF&gt; &amp; test"));
                assertTrue(sheet.contains("r=\"D4\" s=\"4\"><v>"));
                assertFalse(sheet.contains("<f>"));
            }
        }
        finally
        {
            Files.deleteIfExists(target);
        }
    }

    /** Optional local verification. Real data and paths are never stored in Git. */
    @Test
    public void reconcilesOptionalPrivatePortfolioWithPpExport() throws Exception
    {
        var xml = System.getenv("PP_FIFO_PRIVATE_XML");
        var csv = System.getenv("PP_FIFO_PRIVATE_CSV");
        Assume.assumeTrue(xml != null && csv != null);
        {
            var privateClient = ClientFactory.load(Path.of(xml).toFile(), null,
                            new org.eclipse.core.runtime.NullProgressMonitor());
            var privatePortfolio = privateClient.getPortfolios().stream().filter(p -> "Trading 212".equals(p.getName())).findFirst().orElseThrow();
            var report = FifoTaxReport.generate(privateClient, privatePortfolio, 2026);
            var expected = new java.util.HashMap<String, long[]>();
            try (var reader = Files.newBufferedReader(Path.of(csv)); var parser = CSVFormat.DEFAULT.builder()
                            .setDelimiter(';').setHeader().setSkipHeaderRecord(true).get().parse(reader))
            {
                for (var row : parser)
                {
                    if (row.get("Data de término").isBlank())
                        continue;
                    var id = row.get("Nota").split("Trading 212 ID: ")[1].trim();
                    expected.put(id, new long[] { cents(row.get("Valor da entrada")), cents(row.get("Valor de saída")) });
                }
            }
            assertEquals(expected.size(), report.sales().size());
            for (var sale : report.sales())
            {
                var amounts = expected.remove(sale.id());
                assertEquals(amounts[0], sale.cost());
                assertEquals(amounts[1], sale.proceeds());
            }
            assertTrue(expected.isEmpty());
            var output = System.getenv("PP_FIFO_PRIVATE_OUTPUT");
            if (output != null)
                FifoXlsxWriter.write(report, Path.of(output));
        }
    }

    private static long cents(String value)
    {
        return new java.math.BigDecimal(value.replace(".", "").replace(",", ".")).movePointRight(2).longValueExact();
    }
}
