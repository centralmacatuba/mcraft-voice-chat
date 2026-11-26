# Mcraft Voice Chat

Plugin público e reutilizável para Paper, desenvolvido pela [Central Macatuba](https://github.com/centralmacatuba), que implementa o protocolo `minecraft-web-client:voice-chat` e conecta jogadores do Minecraft ao LiveKit.

[Read in English](README.md).

Este é um projeto independente, sem vínculo ou endosso da Prefeitura Municipal de Macatuba.

O plugin foi projetado para servidores Minecraft independentes. Infraestrutura, credenciais, nomes de salas e políticas de moderação específicas de cada servidor pertencem à instalação, não a este repositório.

> **Estado do projeto:** implementação inicial para Paper com emissão de tokens, atualização de proximidade e contrato de integração HTTP para moderação. A compatibilidade ponta a ponta com o cliente upstream e uma implantação LiveKit real ainda precisa ser testada.

## Arquitetura

```text
Minecraft Web Client
  | minecraft-web-client:voice-chat (somente controle/configuração)
  v
Servidor Paper + Mcraft Voice Chat
  | requisições HTTPS de token e moderação
  v
Backend confiável de voz
  | SDK/API administrativa do LiveKit
  v
Sala LiveKit

Minecraft Web Client <====== áudio WebRTC ======> LiveKit
```

O áudio trafega diretamente por WebRTC entre o cliente e o LiveKit. As mensagens de plugin do Minecraft transportam somente dados de controle e configuração.

## Funcionalidades implementadas

- Plugin Paper para o canal existente `minecraft-web-client:voice-chat`.
- Enquadramento do protocolo com duas strings Minecraft `pstring`: ação e payload JSON.
- Emissão individual de tokens LiveKit temporários por backend HTTPS autenticado.
- Validação das claims JWT: algoritmo, UUID do participante, sala, permissões e expiração.
- Participantes silenciados recebem tokens sem permissão de publicação LiveKit quando o backend respeita o contrato.
- Solicitações de mute/unmute no servidor por endpoint de moderação separado e autenticado.
- UUIDs silenciados persistidos e restaurados após reconexões e reinicializações.
- Moderação offline para identidades já presentes no cache de jogadores offline do servidor.
- Fila de solicitações de moderação ordenada por jogador, evitando que respostas antigas de mute/unmute sobrescrevam o estado mais recente.
- Cálculo de proximidade no mesmo mundo e supressão de payloads inalterados.
- Avisos de pacotes malformados com limite de frequência e leitura limitada das respostas do endpoint de tokens.
- Comandos `/mcraftvoice status`, `mute <jogador>` e `unmute <jogador>`.

## Protocolo do cliente

Canal: `minecraft-web-client:voice-chat`.

Cada mensagem contém duas strings:
1. `action`: `pstring` UTF-8 com prefixo de tamanho `i16` assinado;
2. `json`: `pstring` UTF-8 com prefixo de tamanho `i16` assinado.

O JSON deve ser um objeto. Não é JSON bruto e não deve usar a codificação UTF modificada de `DataOutputStream.writeUTF`.

Cliente → servidor: `enable`, `disable`.
Servidor → cliente: `available`, `config`, `nearby`.

## Integração com o backend

O backend é um serviço separado e não está incluído neste repositório. O plugin nunca recebe o segredo da API LiveKit.

Consulte o [contrato do backend em português](docs/backend-contract.pt-BR.md), o [plano de testes de integração](docs/testing.pt-BR.md) ou o [documento em inglês](docs/backend-contract.md).

O endpoint de tokens deve emitir um JWT LiveKit HS256 com o UUID exato do jogador em `sub`, a sala solicitada em `video.room`, `roomJoin=true`, `canSubscribe=true` e expiração curta. Para um jogador silenciado, deve definir `canPublish=false`.

O endpoint de moderação deve persistir o estado de mute e aplicá-lo aos participantes conectados no LiveKit. A política armazenada pelo backend é autoritativa: uma indicação `forceMuted=false` nunca pode remover um mute persistido. Uma resposta 2xx significa que o backend aceitou e armazenou a política de forma durável. Uma indicação `forceMuted` enviada ao cliente não representa aplicação efetiva no servidor.

O plugin valida as claims do JWT, mas não verifica criptograficamente sua assinatura. A assinatura continua sob responsabilidade do backend confiável e do LiveKit. Mantenha a validade dos tokens em até 600 segundos. O LiveKit Cloud revoga tokens existentes quando as permissões do participante mudam; no LiveKit self-hosted, a alteração afeta participantes conectados, mas não invalida tokens já emitidos para reconexões. Um token antigo pode continuar utilizável até expirar, portanto a revogação imediata exige uma camada adicional de controle de admissão.

## Configuração

Defina `MCRAFT_VOICE_BACKEND_TOKEN` no ambiente do processo Paper. Não coloque o segredo em `config.yml` nem no controle de versão.

```yaml
voice-chat:
  enabled: true
  token-provider:
    endpoint: "https://backend.example/voice/token"
    bearer-token-env: "MCRAFT_VOICE_BACKEND_TOKEN"
    request-timeout-seconds: 10
    max-token-lifetime-seconds: 600
  session:
    server-id: "meu-servidor"
    room: "minecraft-survival"
    hearing-range: 32.0
    moderated: true
  moderation:
    endpoint: "https://backend.example/voice/moderation"
    muted-players: []
  proximity:
    update-interval-ticks: 10
```

Os dois endpoints do backend devem usar HTTPS. A URL LiveKit retornada aos clientes deve usar `wss://`; `ws://` só é aceito em loopback para desenvolvimento local.

Se o endpoint de moderação não estiver configurado, os comandos de moderação permanecem indisponíveis. Isso é intencional: uma indicação de mute enviada ao cliente não é uma fronteira de segurança.

## Comandos e permissões

- `/mcraftvoice status` — sessões ativas e estado do backend de moderação.
- `/mcraftvoice mute <jogador>` — persiste e aplica o mute pelo backend.
- `/mcraftvoice unmute <jogador>` — remove a política de mute no backend.
- Alias: `/mcvoice`.

A moderação offline resolve jogadores pelo cache de identidades offline do Paper. O jogador precisa ter entrado no servidor ao menos uma vez; nomes desconhecidos não são resolvidos por consultas de rede bloqueantes.

Permissões: `mcraftvoice.use` (padrão: todos) e `mcraftvoice.moderate` (padrão: operadores).

## Build e releases

Requisitos: Paper API 1.21.11, Java 21 e Gradle Wrapper 9.9.2 incluído.

```bash
./gradlew --no-daemon clean test build
```

O JAR é gerado em `build/libs/`. A CI valida metadados YAML/JSON, executa testes unitários e testes de integração do ciclo de vida em servidor Paper simulado pelo MockBukkit, compila o plugin e disponibiliza o JAR como artefato do workflow. O MockBukkit verifica o ciclo de vida e o registro de comandos em um servidor simulado; ele não substitui os testes ponta a ponta com Minecraft Web Client, backend e LiveKit reais.

As releases usam [Conventional Commits](https://www.conventionalcommits.org/) e [Release Please](https://github.com/googleapis/release-please). Em pushes para `principal`, o Release Please cria ou atualiza um pull request com a versão e o changelog. Ao mesclar esse PR, é criada a tag `vX.Y.Z` e a GitHub Release; o workflow acionado pela tag executa testes/build e anexa o JAR à release existente. Configure o segredo de repositório `RELEASE_PLEASE_TOKEN`, conforme descrito em [CONTRIBUTING.md](CONTRIBUTING.md). O manifesto atualmente considera `0.1.0` como versão-base ainda não publicada.

O suporte a Velocity e a coordenação entre múltiplos servidores estão fora do escopo inicial.

## Contribuição e segurança

Consulte [CONTRIBUTING.md](CONTRIBUTING.md), [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) e [SECURITY.md](SECURITY.md). Nunca publique segredos LiveKit, credenciais do backend, JWTs, dados pessoais de jogadores ou logs de produção.

## Licença

MIT. Consulte [LICENSE](LICENSE).
