package name.abuchen.portfolio.ui.views.trades;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.LocalDate;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.snapshot.trades.FifoTaxReport;
import name.abuchen.portfolio.snapshot.trades.FifoXlsxWriter;
import name.abuchen.portfolio.ui.PortfolioPlugin;

/** Export selection is independent of the current table filters/grouping. */
@SuppressWarnings("nls")
final class FifoExportDialog extends TitleAreaDialog
{
    private final Client client;
    private Combo portfolio;
    private Spinner year;

    FifoExportDialog(Shell shell, Client client)
    {
        super(shell);
        this.client = client;
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        var area = (Composite) super.createDialogArea(parent);
        setTitle("Exportar relatório FIFO");
        setMessage("Compras e vendas em EUR, sem câmbio ou encargos. A exportação usa o histórico completo da conta escolhida.");
        var body = new Composite(area, SWT.NONE);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        body.setLayout(new GridLayout(2, false));
        new Label(body, SWT.NONE).setText("Conta de títulos / instituição");
        portfolio = new Combo(body, SWT.DROP_DOWN | SWT.READ_ONLY);
        portfolio.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        for (var p : client.getPortfolios())
            portfolio.add(p.getName());
        portfolio.select(0);
        for (int i = 0; i < client.getPortfolios().size(); i++)
            if ("Trading 212".equals(client.getPortfolios().get(i).getName()))
                portfolio.select(i);
        new Label(body, SWT.NONE).setText("Ano das vendas");
        year = new Spinner(body, SWT.BORDER);
        year.setMinimum(1900);
        year.setMaximum(9998);
        year.setSelection(LocalDate.now().getYear());
        var scope = new Label(body, SWT.WRAP);
        scope.setText("A conta selecionada deve conter todo o histórico da instituição. O relatório inclui resumo por venda, lotes FIFO e validação interna. A classificação fiscal permanece por confirmar.");
        var layout = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        layout.widthHint = 520;
        scope.setLayoutData(layout);
        return area;
    }

    @Override
    protected void okPressed()
    {
        if (portfolio.getSelectionIndex() < 0)
        {
            setErrorMessage("Escolha uma conta de títulos.");
            return;
        }
        var selected = client.getPortfolios().get(portfolio.getSelectionIndex());
        int selectedYear = year.getSelection();
        var dialog = new FileDialog(getShell(), SWT.SAVE);
        dialog.setText("Guardar relatório FIFO");
        dialog.setFilterExtensions(new String[] { "*.xlsx" });
        dialog.setFilterNames(new String[] { "Excel (*.xlsx)" });
        dialog.setFileName("FIFO-" + selectedYear + ".xlsx");
        dialog.setOverwrite(true);
        var filename = dialog.open();
        if (filename == null)
            return;
        if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"))
            filename += ".xlsx";
        var target = Path.of(filename);
        try
        {
            new ProgressMonitorDialog(getShell()).run(true, false, monitor -> {
                monitor.beginTask("A calcular lotes FIFO e gerar Excel...", 2);
                try
                {
                    var report = FifoTaxReport.generate(client, selected, selectedYear);
                    if (report.sales().isEmpty())
                        throw new IllegalArgumentException("Não existem vendas no ano selecionado.");
                    monitor.worked(1);
                    FifoXlsxWriter.write(report, target);
                    monitor.worked(1);
                }
                catch (Exception e)
                {
                    throw new InvocationTargetException(e);
                }
                finally
                {
                    monitor.done();
                }
            });
            MessageDialog.openInformation(getShell(), "Relatório FIFO", "Relatório guardado em:\n" + target);
            super.okPressed();
        }
        catch (InvocationTargetException e)
        {
            PortfolioPlugin.log(e.getCause());
            setErrorMessage(e.getCause().getMessage());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            setErrorMessage("Exportação interrompida.");
        }
    }
}
