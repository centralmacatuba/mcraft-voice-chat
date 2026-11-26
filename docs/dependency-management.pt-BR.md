# Gerenciamento de dependências

## Paper API 1.21.11

Este projeto tem como alvo Minecraft/Paper 1.21.11 e Java 21. A coordenada Maven documentada pelo Paper para a versão 1.21.11 e anteriores é `1.21.11-R0.1-SNAPSHOT`. A documentação de configuração do projeto Paper afirma explicitamente que, antes da linha 26.1, não há uma forma suportada de selecionar uma compilação específica da API. As novas coordenadas `{VERSION}.build.{BUILD}-stable` aplicam-se ao Paper 26.1 e posteriores e não podem ser adotadas aqui sem alterar a linha de Minecraft suportada e a versão do Java.

Por isso, não substitua a dependência 1.21.11 por uma coordenada de release presumida nem por um nome de arquivo timestamped. Mantenha a coordenada oficial do Paper e use a verificação de dependências do Gradle como uma proteção de integridade que falha de forma segura:

- `gradle/verification-metadata.xml` verifica os artefatos e metadados resolvidos da API Paper usando SHA-256.
- Se o Paper republicar os metadados ou o artefato SNAPSHOT, um build com cache Gradle limpo deve falhar na verificação, em vez de aceitar silenciosamente novos bytes.
- Antes de atualizar intencionalmente o SNAPSHOT da API Paper, resolva-o com o cache Gradle limpo, inspecione as mudanças nas dependências, regenere os metadados SHA-256 e envie as alterações revisadas juntas.
- Nunca use `--write-verification-metadata` como reparo automático na CI nem desative a verificação para contornar uma divergência.
- Builds de release devem partir do commit exato do código-fonte e passar pela mesma verificação.

Reavalie esta decisão somente quando o projeto elevar intencionalmente a versão mínima de servidor para Paper 26.1 ou posterior e adotar a versão correspondente do Java. Nesse momento, fixe uma coordenada imutável `{VERSION}.build.{BUILD}-stable`, em vez de usar uma versão dinâmica.

## Dependências transitivas

A API Paper e o MockBukkit trazem dependências transitivas para os classpaths de compilação e testes. Mantenha completo o arquivo de verificação de dependências e avalie os alertas do OSV considerando o grafo de dependências resolvido e o JAR final do plugin. Uma dependência `compileOnly` não é empacotada no JAR do plugin, mas isso não justifica ignorar uma biblioteca vulnerável usada pelas ferramentas de teste ou execução.
