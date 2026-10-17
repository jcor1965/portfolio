# Versão pessoal do Portfolio Performance

Este fork mantém as adaptações pessoais separadas do histórico oficial. A aplicação instalada, carteiras, CSV e relatórios reais ficam fora deste repositório.

## Organização

| Referência         | Finalidade                                             |
|--------------------|--------------------------------------------------------|
| `origin`           | Fork pessoal no GitHub                                 |
| `upstream`         | Repositório oficial; usado para obter atualizações     |
| `master`           | Referência do desenvolvimento oficial                  |
| `personal/main`    | Base estável e adaptações pessoais                     |
| `personal/base/*`  | Tags locais que identificam as versões oficiais usadas |

Base inicial: tag oficial `0.89.0`, commit `20d7fb690`. A instalação experimental 0.89.1 preexistente não é a base deste fork e não foi substituída.

## Desenvolvimento e compilação

Usar Java 21 e Maven. Nesta máquina, Java e Maven portátil estão disponíveis na pasta que contém este checkout, sem instalação global adicional.

```powershell
# Validar a configuração e resolver a plataforma de dependências
& .\personal\Build.ps1 -Mode Validate

# Compilar e executar os testes do núcleo
& .\personal\Build.ps1 -Mode CoreTests

# Compilar todos os módulos sem repetir os testes
& .\personal\Build.ps1 -Mode Compile

# Executar a compilação completa, testes e empacotamento oficiais
& .\personal\Build.ps1 -Mode Full
```

`Build.ps1` aceita `-JavaHome`, `-MavenHome` e `-LocalRepository`. O perfil `local-dev` é usado nos modos CoreTests e Compile; omite cobertura, Checkstyle, geração protobuf e traduções conforme a configuração oficial. Compile não executa testes e deve ser usado depois de validar as alterações. O modo Full usa o perfil normal. A primeira execução necessita de rede para obter as dependências.

No Windows, o script usa os certificados de confiança do sistema para as ligações HTTPS do Java. Não desativa a verificação de certificados. As variáveis de ambiente são restauradas ao terminar.

## Adaptações pessoais

### Exportação FIFO

Em Relatórios > Performance > Negociações, o menu de exportação inclui **Exportar relatório FIFO (Excel)**. O diálogo permite escolher a conta de títulos e o ano das vendas. Usa todo o histórico anterior da conta, independentemente dos filtros da tabela, e não altera a carteira.

O Excel contém resumo por venda, parcelas por aquisição e validação interna. Inclui datas, quantidades, valores em EUR, ISIN, IDs/UUID e ficheiros de origem. Os atributos **País da fonte — Nome** e **País da fonte — AT** são lidos pelo nome do atributo, sem depender dos UUID locais.

Esta primeira versão suporta compras/vendas em EUR sem unidades de encargos. Rejeita câmbio, entregas, transferências, vendas sem histórico e IDs Trading 212 repetidos. A conta escolhida deve corresponder a todo o histórico da instituição. Ainda não consolida várias contas da mesma instituição. Não calcula impostos nem determina códigos fiscais.

O rateio usa quantidades inteiras e aritmética inteira de precisão arbitrária, arredondando cumulativamente a cêntimos. Preserva o custo de cada lote consumido e a receita de cada venda. Empates de data/hora respeitam a ordem na carteira. O gerador externo continua a ser uma referência local para a reconciliação.

### Importação Trading 212

O menu Ficheiro > Importar inclui **Trading 212 (CSV)**. O extrator do complemento foi transportado para o núcleo e ligado ao assistente padrão do PP. Mantém os limites funcionais do complemento, incluindo a deduplicação apenas dentro do mesmo lote e a rejeição de câmbio/encargos adicionais não nulos.

### Testes e pacote de teste

Os testes públicos usam dados sintéticos. A reconciliação opcional com dados reais só corre quando existem `PP_FIFO_PRIVATE_XML` e `PP_FIFO_PRIVATE_CSV` no ambiente local. `PP_FIFO_PRIVATE_OUTPUT` permite guardar o Excel dessa verificação. Estes dados não são necessários para compilar nem são incluídos no Git.

`Package-Test.ps1` cria uma distribuição isolada numa pasta nova, a partir de um ZIP oficial 0.89.0 cujo SHA-256 é fornecido e verificado. Substitui apenas os módulos core, UI e bootstrap pelos JAR compilados, acrescenta o Java local e um lançador com workspace/preferências separados. As atualizações automáticas ficam desativadas para não substituir as adaptações pelo programa oficial. Não substitui a instalação habitual e não inclui carteiras reais.

```powershell
& .\personal\Package-Test.ps1 -OfficialZip <zip-oficial> -OfficialSha256 <sha256-do-release>
```

Abrir a distribuição por `Abrir-PP-Pessoal.cmd` e testar numa cópia da carteira: o isolamento das preferências não impede a gravação de um ficheiro aberto. O pacote é experimental e os módulos adaptados não têm a assinatura da distribuição oficial.

Concentrar o código novo em classes/pacotes próprios. Alterar os componentes existentes apenas onde necessário para ligar os comandos, dados e exportação. Manter cada adaptação em commits pequenos e identificáveis.

## Atualizar para uma versão oficial

1. Confirmar que o checkout não tem alterações pendentes e que `personal/main` está guardada no fork.
2. Obter as tags oficiais com `git fetch upstream --tags`.
3. Criar uma branch de atualização a partir de `personal/main`.
4. Integrar a nova tag oficial com `git merge <tag-oficial>` e resolver conflitos. Não reescrever o histórico publicado de `personal/main`.
5. Executar CoreTests, depois Full. Rever alterações no modelo, persistência, importadores, cálculos FIFO e interface.
6. Testar a nova aplicação numa pasta e ambiente separados, usando uma cópia da carteira. Confirmar o relatório e os totais antes de substituir a instalação em uso.
7. Integrar a branch de atualização em `personal/main` depois de passar a validação e registar a nova base oficial numa tag `personal/base/<versão>`.

Os testes CI pessoais executam os testes do núcleo quando há alterações na branch pessoal. O empacotamento completo e o teste interativo continuam a ser necessários antes de usar uma nova distribuição.

## Dados e segurança

Não guardar credenciais, tokens, carteiras reais, CSV de corretoras ou Excel pessoais no Git. Usar `personal/private/` apenas para ficheiros locais ignorados, quando necessário. O ficheiro `.gitignore` protege essa pasta e `personal/output/`.

O remote `upstream` local tem o push desativado. Os commits pessoais são enviados apenas para `origin`. Num novo clone, voltar a adicionar `upstream` e desativar o seu push antes de trabalhar.
