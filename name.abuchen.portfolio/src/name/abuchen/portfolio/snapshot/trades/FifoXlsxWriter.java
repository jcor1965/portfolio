package name.abuchen.portfolio.snapshot.trades;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.trades.FifoTaxReport.Report;

/** Small Office Open XML exporter with no additional runtime dependencies. */
@SuppressWarnings("nls")
public final class FifoXlsxWriter
{
    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    private record Cell(Object value, int style) { }

    private FifoXlsxWriter() { }

    public static void write(Report report, Path destination) throws IOException
    {
        var target = destination.toAbsolutePath();
        var temporary = Files.createTempFile(target.getParent(), "fifo-", ".xlsx.tmp");
        try
        {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporary)))
            {
                entry(zip, "[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                                + java.util.stream.IntStream.rangeClosed(1, 3).mapToObj(i -> "<Override PartName=\"/xl/worksheets/sheet" + i + ".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>").collect(java.util.stream.Collectors.joining()) + "</Types>");
                entry(zip, "_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
                entry(zip, "xl/workbook.xml", "<workbook xmlns=\"" + NS + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Resumo de vendas\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Detalhe FIFO\" sheetId=\"2\" r:id=\"rId2\"/><sheet name=\"Validação\" sheetId=\"3\" r:id=\"rId3\"/></sheets></workbook>");
                var relationships = new StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
                for (int i = 1; i <= 3; i++)
                    relationships.append("<Relationship Id=\"rId").append(i).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
                relationships.append("<Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
                entry(zip, "xl/_rels/workbook.xml.rels", relationships.toString());
                entry(zip, "xl/styles.xml", styles());
                entry(zip, "xl/worksheets/sheet1.xml", summary(report));
                entry(zip, "xl/worksheets/sheet2.xml", detail(report));
                entry(zip, "xl/worksheets/sheet3.xml", validation(report));
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    private static String summary(Report report)
    {
        var rows = new ArrayList<List<Cell>>();
        rows.add(List.of(text("FIFO " + report.year() + " — " + report.portfolio())));
        rows.add(List.of(text("Provisório. Classificação fiscal por validar. Dados até " + report.latestTransaction())));
        rows.add(headers("ID venda", "Ativo", "ISIN", "Data venda", "Quantidade", "Lotes", "Aquisição EUR", "Realização EUR", "Resultado EUR", "País da fonte", "Código AT", "UUID venda"));
        for (var s : report.sales())
            rows.add(List.of(text(s.id()), text(s.name()), text(s.isin()), date(s.date()), shares(s.shares()), number(s.lots()), money(s.cost()), money(s.proceeds()), money(s.gain()), text(s.country()), text(s.countryCode()), text(s.uuid())));
        rows.add(List.of(text("TOTAL"), text(""), text(""), text(""), text(""), text(""), money(report.sales().stream().mapToLong(s -> s.cost()).sum()), money(report.sales().stream().mapToLong(s -> s.proceeds()).sum()), money(report.sales().stream().mapToLong(s -> s.gain()).sum())));
        return sheet(rows, 3, 12, true);
    }

    private static String detail(Report report)
    {
        var rows = new ArrayList<List<Cell>>();
        rows.add(List.of(text("Detalhe FIFO " + report.year() + " — " + report.portfolio())));
        rows.add(List.of(text("Rateio cumulativo HALF_UP a cêntimos. Valores preservados por lote e venda.")));
        rows.add(headers("ID venda", "Ativo", "ISIN", "Data aquisição", "Data venda", "Quantidade", "Aquisição EUR", "Realização EUR", "Resultado EUR", "Detenção dias", "ID compra", "País da fonte", "Código AT", "UUID compra", "UUID venda", "Origem compra", "Origem venda"));
        for (var l : report.lots())
            rows.add(List.of(text(l.saleId()), text(l.name()), text(l.isin()), date(l.purchased()), date(l.sold()), shares(l.shares()), money(l.cost()), money(l.proceeds()), money(l.gain()), number(l.holdingDays()), text(l.purchaseId()), text(l.country()), text(l.countryCode()), text(l.purchaseUuid()), text(l.saleUuid()), text(l.purchaseSource()), text(l.saleSource())));
        return sheet(rows, 3, 17, true);
    }

    private static String validation(Report report)
    {
        var rows = new ArrayList<List<Cell>>();
        rows.add(List.of(text("Validação interna FIFO " + report.year())));
        rows.add(List.of(text("Comparação do resumo com o detalhe. Não é uma reconciliação com extratos externos.")));
        rows.add(headers("ID venda", "Ativo", "Qtd. venda", "Qtd. lotes", "Dif. quantidade", "Aquisição resumo", "Aquisição lotes", "Dif. aquisição", "Realização venda", "Realização lotes", "Dif. realização", "Estado"));
        for (var s : report.sales())
        {
            var parts = report.lots().stream().filter(l -> l.saleUuid().equals(s.uuid())).toList();
            long q = parts.stream().mapToLong(l -> l.shares()).sum();
            long c = parts.stream().mapToLong(l -> l.cost()).sum();
            long p = parts.stream().mapToLong(l -> l.proceeds()).sum();
            rows.add(List.of(text(s.id()), text(s.name()), shares(s.shares()), shares(q), shares(q - s.shares()), money(s.cost()), money(c), money(c - s.cost()), money(s.proceeds()), money(p), money(p - s.proceeds()), text(q == s.shares() && c == s.cost() && p == s.proceeds() ? "Coincide" : "Diferença")));
        }
        rows.add(List.of(text("")));
        for (var warning : report.warnings())
            rows.add(List.of(text(warning)));
        rows.add(List.of(text("Novos movimentos: voltar a exportar. A seleção dos lotes não se recalcula no Excel.")));
        return sheet(rows, 3, 12, false);
    }

    private static Cell text(String value) { return new Cell(value == null ? "" : value, 0); }
    private static Cell number(long value) { return new Cell(value, 0); }
    private static Cell money(long value) { return new Cell(BigDecimal.valueOf(value, Values.Amount.precision()), 2); }
    private static Cell shares(long value) { return new Cell(BigDecimal.valueOf(value, Values.Share.precision()), 3); }
    private static Cell date(LocalDateTime value)
    {
        return new Cell(BigDecimal.valueOf(ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), value.toLocalDate()))
                        .add(BigDecimal.valueOf(value.toLocalTime().toSecondOfDay() / 86400d)), 4);
    }
    private static List<Cell> headers(String... values)
    {
        return java.util.Arrays.stream(values).map(v -> new Cell(v, 1)).toList();
    }

    private static String sheet(List<List<Cell>> rows, int frozen, int columns, boolean filter)
    {
        var xml = new StringBuilder("<worksheet xmlns=\"").append(NS).append("\"><sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\"><pane xSplit=\"2\" ySplit=\"").append(frozen).append("\" topLeftCell=\"C4\" activePane=\"bottomRight\" state=\"frozen\"/></sheetView></sheetViews><cols>");
        for (int c = 1; c <= columns; c++)
            xml.append("<col min=\"").append(c).append("\" max=\"").append(c).append("\" width=\"").append(c == 2 ? 65 : 24).append("\" customWidth=\"1\"/>");
        xml.append("</cols><sheetData>");
        for (int r = 0; r < rows.size(); r++)
        {
            xml.append("<row r=\"").append(r + 1).append("\" ht=\"").append(r == 2 ? 32 : 21).append("\" customHeight=\"1\">");
            for (int c = 0; c < rows.get(r).size(); c++)
            {
                var cell = rows.get(r).get(c);
                var column = c < 26 ? String.valueOf((char) ('A' + c)) : "A" + (char) ('A' + c - 26);
                xml.append("<c r=\"").append(column).append(r + 1).append("\" s=\"").append(cell.style()).append('"');
                if (cell.value() instanceof Number)
                    xml.append("><v>").append(cell.value()).append("</v></c>");
                else
                    xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(cell.value().toString())).append("</t></is></c>");
            }
            xml.append("</row>");
        }
        xml.append("</sheetData>");
        if (filter && rows.size() > 3)
            xml.append("<autoFilter ref=\"A3:").append((char) ('A' + columns - 1)).append(rows.size() - (columns == 12 ? 1 : 0)).append("\"/>");
        return xml.append("</worksheet>").toString();
    }

    private static String escape(String value)
    {
        var clean = new StringBuilder();
        value.codePoints().filter(c -> c == 9 || c == 10 || c == 13 || (c >= 32 && c <= 0xd7ff)
                        || (c >= 0xe000 && c <= 0xfffd) || (c >= 0x10000 && c <= 0x10ffff))
                        .forEach(clean::appendCodePoint);
        return clean.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void entry(ZipOutputStream zip, String name, String xml) throws IOException
    {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + xml).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String styles()
    {
        return "<styleSheet xmlns=\"" + NS + "\"><numFmts count=\"3\"><numFmt numFmtId=\"164\" formatCode=\"#,##0.00;(#,##0.00);0.00\"/><numFmt numFmtId=\"165\" formatCode=\"0.00000000\"/><numFmt numFmtId=\"166\" formatCode=\"dd/mm/yyyy hh:mm:ss\"/></numFmts>"
                        + "<fonts count=\"2\"><font><sz val=\"10\"/><name val=\"Arial\"/></font><font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"10\"/><name val=\"Arial\"/></font></fonts>"
                        + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF203B55\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                        + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                        + "<cellXfs count=\"5\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf><xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";
    }
}
