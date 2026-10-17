package name.abuchen.portfolio.datatransfer.trading212;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.ArrayList;

import org.junit.Test;

import name.abuchen.portfolio.datatransfer.Extractor.InputFile;
import name.abuchen.portfolio.datatransfer.SecurityCache;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Client;

@SuppressWarnings("nls")
public class Trading212CSVExtractorTest
{
    @Test
    public void importsSyntheticBrokerActivityAndRejectsSameBatchOverlap() throws Exception
    {
        var file = Files.createTempFile("trading212-test-", ".csv");
        try
        {
            try (var source = getClass().getResourceAsStream("activity.csv"))
            {
                Files.copy(source, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            var client = new Client();
            var extractor = new Trading212CSVExtractor(client);
            var cache = new SecurityCache(client);
            var errors = new ArrayList<Exception>();
            var items = extractor.extract(cache, new InputFile(file.toFile()), errors);
            assertTrue(errors.toString(), errors.isEmpty());
            assertEquals(7, items.size());
            assertEquals(2, items.stream().filter(i -> i.getSubject() instanceof name.abuchen.portfolio.model.BuySellEntry).count());
            assertTrue(items.stream().anyMatch(i -> i.getSubject() instanceof AccountTransaction t
                            && t.getType() == AccountTransaction.Type.DIVIDENDS && t.getUnits().findAny().isPresent()));
            assertTrue(extractor.extract(cache, new InputFile(file.toFile()), errors).isEmpty());
            assertFalse(items.stream().anyMatch(i -> i.isFailure()));
        }
        finally
        {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsConflictingBrokerIds() throws Exception
    {
        var file = Files.createTempFile("trading212-conflict-", ".csv");
        try
        {
            Files.writeString(file, "Action,Time (UTC),Total,Currency (Total),ISIN,Ticker,Name,No. of shares,ID\n"
                            + "Deposit,2026-01-01 10:00:00+00:00,100,EUR,,,,,D1\n"
                            + "Deposit,2026-01-01 10:00:00+00:00,101,EUR,,,,,D1\n");
            var client = new Client();
            var errors = new ArrayList<Exception>();
            var items = new Trading212CSVExtractor(client).extract(new SecurityCache(client), new InputFile(file.toFile()), errors);
            assertEquals(1, errors.size());
            assertTrue(items.getFirst().isFailure());
        }
        finally
        {
            Files.deleteIfExists(file);
        }
    }
}
