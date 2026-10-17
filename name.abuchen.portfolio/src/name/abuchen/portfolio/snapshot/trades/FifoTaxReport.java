package name.abuchen.portfolio.snapshot.trades;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;

/** Read-only, EUR-only FIFO report. Does not determine tax codes or tax due. */
@SuppressWarnings("nls")
public final class FifoTaxReport
{
    private static final Pattern BROKER_ID = Pattern.compile("Trading 212 ID:\\s*([^|]+)");

    public record Lot(String saleId, String purchaseId, String saleUuid, String purchaseUuid, String name,
                    String isin, LocalDateTime purchased, LocalDateTime sold, long shares, long cost, long proceeds,
                    String country, String countryCode, String purchaseSource, String saleSource)
    {
        public long gain() { return Math.subtractExact(proceeds, cost); }
        public long holdingDays() { return ChronoUnit.DAYS.between(purchased.toLocalDate(), sold.toLocalDate()); }
    }

    public record Sale(String id, String uuid, String name, String isin, LocalDateTime date, long shares,
                    long cost, long proceeds, int lots, String country, String countryCode)
    {
        public long gain() { return Math.subtractExact(proceeds, cost); }
    }

    public record Report(int year, String portfolio, LocalDateTime latestTransaction, List<Lot> lots,
                    List<Sale> sales, List<String> warnings)
    {
        public Report
        {
            lots = List.copyOf(lots);
            sales = List.copyOf(sales);
            warnings = List.copyOf(warnings);
        }
    }

    private static final class OpenLot
    {
        private final PortfolioTransaction purchase;
        private long consumed;
        private long allocated;

        private OpenLot(PortfolioTransaction purchase) { this.purchase = purchase; }
    }

    private FifoTaxReport() { }

    /**
     * The selected securities account is the FIFO boundary. All acquisitions and
     * sales through the selected year are processed, including earlier years.
     * Transfers and FX are rejected rather than silently losing historic costs.
     */
    public static Report generate(Client client, Portfolio portfolio, int year)
    {
        if (!client.getPortfolios().contains(portfolio) || year < 1900 || year > 9998)
            throw new IllegalArgumentException("Carteira ou ano inválido.");

        var transactions = portfolio.getTransactions().stream()
                        .filter(t -> t.getDateTime().getYear() <= year)
                        .sorted(Comparator.comparing(PortfolioTransaction::getDateTime)).toList();
        var queues = new HashMap<String, ArrayDeque<OpenLot>>();
        var identifiers = new HashSet<String>();
        var warnings = new ArrayList<String>();
        var times = new HashSet<String>();
        var lots = new ArrayList<Lot>();
        var sales = new ArrayList<Sale>();

        for (var transaction : transactions)
        {
            if (transaction.getType() != PortfolioTransaction.Type.BUY
                            && transaction.getType() != PortfolioTransaction.Type.SELL)
                throw new IllegalArgumentException("Movimento não suportado no relatório FIFO: " + transaction.getType());
            if (!"EUR".equals(transaction.getCurrencyCode()) || transaction.getUnits().findAny().isPresent())
                throw new IllegalArgumentException("Este relatório requer movimentos em EUR sem encargos ou câmbio: "
                                + transaction.getDateTime());
            var security = transaction.getSecurity();
            if (security == null || security.getIsin() == null || security.getIsin().isBlank()
                            || transaction.getShares() <= 0 || transaction.getAmount() < 0)
                throw new IllegalArgumentException("Movimento sem ISIN, quantidade ou valor válido.");
            var id = brokerId(transaction);
            if (!id.isBlank() && !identifiers.add(id))
                throw new IllegalArgumentException("ID Trading 212 repetido: " + id);
            var queue = queues.computeIfAbsent(security.getIsin(), key -> new ArrayDeque<>());

            if (transaction.getType() == PortfolioTransaction.Type.BUY)
            {
                if (!times.add(security.getIsin() + "|" + transaction.getDateTime()) && warnings.isEmpty())
                    warnings.add("Compras com a mesma data/hora mantêm a ordem na carteira.");
                queue.add(new OpenLot(transaction));
                continue;
            }

            long remaining = transaction.getShares();
            long consumed = 0;
            long allocated = 0;
            long totalCost = 0;
            int count = 0;
            while (remaining > 0)
            {
                if (queue.isEmpty())
                    throw new IllegalArgumentException("Histórico de aquisição insuficiente para a venda: " + id);
                var open = queue.getFirst();
                var purchase = open.purchase;
                long quantity = Math.min(remaining, purchase.getShares() - open.consumed);
                long purchaseAllocated = allocate(purchase.getAmount(), Math.addExact(open.consumed, quantity),
                                purchase.getShares());
                long cost = purchaseAllocated - open.allocated;
                long saleAllocated = allocate(transaction.getAmount(), Math.addExact(consumed, quantity),
                                transaction.getShares());
                long proceeds = saleAllocated - allocated;
                if (transaction.getDateTime().getYear() == year)
                    lots.add(new Lot(id, brokerId(purchase), transaction.getUUID(), purchase.getUUID(),
                                    security.getName(), security.getIsin(), purchase.getDateTime(),
                                    transaction.getDateTime(), quantity, cost, proceeds,
                                    attribute(client, security, "País da fonte — Nome"),
                                    attribute(client, security, "País da fonte — AT"),
                                    purchase.getSource(), transaction.getSource()));
                remaining -= quantity;
                consumed += quantity;
                allocated = saleAllocated;
                totalCost = Math.addExact(totalCost, cost);
                open.consumed += quantity;
                open.allocated = purchaseAllocated;
                count++;
                if (open.consumed == purchase.getShares())
                    queue.removeFirst();
            }
            if (transaction.getDateTime().getYear() == year)
                sales.add(new Sale(id, transaction.getUUID(), security.getName(), security.getIsin(),
                                transaction.getDateTime(), transaction.getShares(), totalCost, transaction.getAmount(),
                                count, attribute(client, security, "País da fonte — Nome"),
                                attribute(client, security, "País da fonte — AT")));
        }
        if (sales.stream().anyMatch(s -> s.country().isBlank() || s.countryCode().isBlank()))
            warnings.add("Existem títulos sem país da fonte ou código AT preenchido.");
        warnings.add("Relatório por conta de títulos. Contas na mesma instituição devem ser reconciliadas em conjunto.");
        warnings.add("Classificação fiscal e ajustamentos fiscais por validar. Não calcula imposto.");
        return new Report(year, portfolio.getName(), transactions.isEmpty() ? null
                        : transactions.getLast().getDateTime(), lots, sales, warnings);
    }

    private static long allocate(long amount, long quantity, long total)
    {
        // Integer HALF_UP, with a BigInteger intermediate to avoid overflow.
        return BigInteger.valueOf(amount).multiply(BigInteger.valueOf(quantity)).multiply(BigInteger.TWO)
                        .add(BigInteger.valueOf(total)).divide(BigInteger.valueOf(total).multiply(BigInteger.TWO))
                        .longValueExact();
    }

    private static String brokerId(PortfolioTransaction transaction)
    {
        var matcher = BROKER_ID.matcher(transaction.getNote() == null ? "" : transaction.getNote());
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String attribute(Client client, Security security, String name)
    {
        return client.getSettings().getAttributeTypes().filter(a -> name.equals(a.getName()))
                        .map(a -> security.getAttributes().get(a)).filter(java.util.Objects::nonNull)
                        .map(Object::toString).findFirst().orElse("");
    }
}
