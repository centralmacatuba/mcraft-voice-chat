# Mcraft Voice Chat

Plugin público para Paper, desenvolvido pela [Central Macatuba](https://github.com/centralmacatuba), que implementa o protocolo de mensagens `minecraft-web-client:voice-chat` e integra jogadores do Minecraft a uma sala LiveKit.

[Read in English](README.md).

O plugin foi projetado para ser reutilizado por servidores Minecraft independentes. Infraestrutura, credenciais, nomes de salas e políticas de moderação específicas de cada servidor devem ficar na configuração da instalação, não neste repositório.

> **Estado do projeto:** planejamento e implementação inicial. O enquadramento dos pacotes foi conferido com o código atual do cliente `minecraft-web-client`. A compatibilidade ponta a ponta com um cliente real e uma implantação LiveKit ainda não foi validada.

## Objetivos

- Implementar o canal existente `minecraft-web-client:voice-chat`, sem exigir modificações no cliente.
- Ter Paper como plataforma inicial, mantendo a lógica central isolada para uma possível integração futura com Velocity.
- Não transportar mídia de voz pelas mensagens do Minecraft. O áudio WebRTC trafega diretamente entre os clientes e o LiveKit.
- Coordenar sessões de voz, proximidade e moderação a partir do servidor Minecraft.
- Suportar LiveKit Cloud e LiveKit self-hosted por meio de um backend configurável para emissão de tokens.
- Manter credenciais e endpoints específicos dos servidores fora do controle de versão.
- Oferecer testes, builds reproduzíveis, verificações de segurança e documentação operacional.

## Arquitetura

```text
Minecraft Web Client
  |  minecraft-web-client:voice-chat (somente controle/configuração)
  v
Servidor Paper + Mcraft Voice Chat
  |  requisição HTTPS autenticada para obter token temporário
  v
Backend de tokens de voz
  |
  +---- Credenciais LiveKit / permissões de participantes

Minecraft Web Client <====== áudio WebRTC ======> LiveKit
```

O plugin Paper:

- anuncia suporte a voice chat para clientes compatíveis;
- recebe as ações `enable` e `disable`;
- solicita um token LiveKit individual e de curta duração ao backend configurado;
- envia ao cliente os dados necessários para conectar ao LiveKit;
- calcula a lista autoritativa de jogadores próximos que participam do voice chat;
- envia informações de proximidade e estado de moderação;
- limpa o estado dos jogadores ao desconectar e trata falhas assíncronas na emissão de tokens.

O cliente continua responsável pela captura do microfone, conexão WebRTC com LiveKit, inscrições nas faixas de áudio, posicionamento 3D e atenuação espacial.

## Protocolo do cliente

Canal: `minecraft-web-client:voice-chat`

O cliente atual registra uma estrutura de pacote com duas strings do protocolo Minecraft:

1. `action`: `pstring` com prefixo de tamanho `i16`;
2. `json`: `pstring` com prefixo de tamanho `i16`.

A segunda string contém um objeto JSON. Portanto, **não** é uma mensagem de plugin contendo JSON bruto e não deve ser codificada com o formato UTF modificado de `DataOutputStream.writeUTF`.

### Cliente → servidor

| Ação | Payload | Significado |
| --- | --- | --- |
| `enable` | `{}` | O jogador ativa o voice chat. |
| `disable` | `{}` | O jogador desativa o voice chat. |

### Servidor → cliente

| Ação | Payload | Significado |
| --- | --- | --- |
| `available` | `{}` | O servidor oferece suporte ao voice chat. |
| `config` | `VoiceConfig` | Dados de conexão LiveKit individuais do jogador. |
| `nearby` | `NearbyPayload` | Jogadores que o cliente deve conseguir ouvir e identidades forçadas ao estado de mudo. |

Formato esperado de `VoiceConfig`:

```json
{
  "url": "wss://seu-servidor-livekit",
  "room": "minecraft-survival",
  "token": "<token-temporario-do-participante>",
  "identity": "<uuid-do-jogador-minecraft>",
  "hearingRange": 32,
  "moderated": true
}
```

Formato esperado de `NearbyPayload`:

```json
{
  "players": [
    {
      "identity": "<uuid-do-jogador-minecraft>",
      "username": "Jogador"
    }
  ],
  "forceMuted": []
}
```

O protocolo é baseado na implementação do cliente, não em um formato inventado pelo plugin. Consulte novamente o código upstream do cliente ao atualizar a compatibilidade.

## Contrato do backend de tokens

O plugin nunca deve expor o segredo da API LiveKit ao navegador nem armazená-lo neste repositório. Ele chama um backend confiável por HTTPS usando uma credencial Bearer mantida no servidor.

Contrato HTTP inicial:

- Requisição: `POST <token-provider.endpoint>`
- Cabeçalho: `Authorization: Bearer <segredo-do-servidor>`
- Corpo JSON:

```json
{
  "playerUuid": "00000000-0000-0000-0000-000000000000",
  "username": "Jogador",
  "serverId": "mcraft",
  "room": "minecraft-survival"
}
```

Resposta JSON:

```json
{
  "url": "wss://seu-servidor-livekit",
  "room": "minecraft-survival",
  "token": "<token-temporario-do-participante>"
}
```

O backend deve emitir um token cuja identidade do participante (`sub`) corresponda ao UUID Minecraft recebido, limitar o token à sala solicitada, definir permissões apropriadas de publicação/inscrição e usar expiração curta. O backend deve autenticar a credencial do servidor e validar a requisição; não deve confiar cegamente em identidades arbitrárias enviadas por clientes públicos.

## Configuração

A implementação utiliza `config.yml` para configurações específicas do servidor. Segredos devem ser fornecidos por variáveis de ambiente ou outro mecanismo seguro, nunca enviados ao Git.

Variável de ambiente padrão para a credencial do backend: `MCRAFT_VOICE_BACKEND_TOKEN`.

Consulte o arquivo `src/main/resources/config.yml` para ver as opções previstas.

## Compatibilidade e requisitos

- Plataforma: Paper (alvo inicial).
- API Minecraft: 1.21.11.
- Java: 21.
- Cliente: versão do Minecraft Web Client que contenha a implementação de `minecraft-web-client:voice-chat`.
- Transporte de voz: LiveKit via WebRTC; o proxy Minecraft não funciona como retransmissor de áudio.

Velocity e coordenação de múltiplos servidores backend não fazem parte do escopo inicial.

## Planejamento de desenvolvimento

### Fase 1 — Protocolo e base Paper
- [ ] Criar o projeto Gradle e a classe principal do plugin.
- [ ] Implementar o enquadramento exato `pstring/i16` e validar os payloads JSON.
- [ ] Registrar os canais de mensagens de entrada e saída.
- [ ] Anunciar `available` aos clientes compatíveis.
- [ ] Tratar com segurança as ações `enable` e `disable`.
- [ ] Adicionar testes unitários para codificação e decodificação dos pacotes.

### Fase 2 — Criação de sessões LiveKit
- [ ] Implementar o cliente HTTP autenticado para o backend de tokens.
- [ ] Validar as respostas do backend e vincular a identidade do token ao UUID Minecraft.
- [ ] Enviar o payload `config` esperado pelo cliente.
- [ ] Tratar timeouts, erros, solicitações duplicadas e desconexões durante requisições.

### Fase 3 — Proximidade e moderação
- [ ] Calcular jogadores próximos que participam do voice chat no mesmo mundo.
- [ ] Enviar `nearby` somente quando o payload mudar.
- [ ] Permitir configurar o alcance de audição e o intervalo de atualização.
- [ ] Adicionar estado de mudo controlado pelo servidor e integração com permissões.
- [ ] Limpar o estado em mudanças de mundo, desconexões e encerramento do plugin.

### Fase 4 — Verificação e publicação
- [ ] Testar com o Minecraft Web Client sem modificações.
- [ ] Verificar áudio entre dois ou mais jogadores pelo LiveKit.
- [ ] Testar reconexões, expiração de tokens, mudo, mudanças de mundo e encaminhamento pelo proxy.
- [ ] Finalizar documentação operacional e automação de releases.
- [ ] Revisar segurança das dependências e reprodutibilidade do build.

## Desenvolvimento

O projeto utiliza Gradle e a toolchain Java definida no build.

```bash
gradle test
gradle build
```

O JAR do plugin é gerado em `build/libs/`.

## Licença

Este projeto é distribuído sob a licença MIT. Consulte o arquivo [LICENSE](LICENSE).
