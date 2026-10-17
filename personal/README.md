# Versão pessoal do Portfolio Performance

Este fork mantém as adaptações pessoais separadas do histórico oficial. A aplicação instalada, carteiras, CSV e relatórios reais ficam fora deste repositório.

## Organização

| Referência         | Finalidade                                             |
|--------------------|--------------------------------------------------------|
| `origin`           | Fork pessoal no GitHub                                 |
| `upstream`         | Repositório oficial; usado para obter atualizações      |
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

# Executar a compilação completa, testes e empacotamento oficiais
& .\personal\Build.ps1 -Mode Full
```

`Build.ps1` aceita `-JavaHome`, `-MavenHome` e `-LocalRepository`. O perfil `local-dev` só é usado no modo CoreTests; omite cobertura, Checkstyle, geração protobuf e traduções conforme a configuração oficial. O modo Full usa o perfil normal. A primeira execução necessita de rede para obter as dependências.

No Windows, o script usa os certificados de confiança do sistema para as ligações HTTPS do Java. Não desativa a verificação de certificados. As variáveis de ambiente são restauradas ao terminar.

## Adaptações planeadas

Primeira funcionalidade: exportação FIFO anual, com resumo por venda, detalhe por aquisição e atributos fiscais dos títulos. A integração ainda não está implementada neste fork.

O gerador externo existente serve de referência funcional local. Ao integrá-lo, usar dados sintéticos nos testes e confirmar quantidades, rateio monetário, vendas parciais, anos anteriores e correspondência com o cálculo do PP. Nenhum relatório ou carteira real deve ser publicado como fixture de teste.

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
