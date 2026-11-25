# Mcraft Voice Chat

[Read in English](README.md).

Plugin público para Paper, desenvolvido pela [Central Macatuba](https://github.com/centralmacatuba), que implementa o protocolo de mensagens `minecraft-web-client:voice-chat` e integra jogadores do Minecraft a uma sala LiveKit.

O plugin foi projetado para ser reutilizado por servidores Minecraft independentes. Infraestrutura, credenciais, nomes de salas e políticas de moderação específicas de cada servidor devem ficar na configuração da instalação, não neste repositório.

> **Estado do projeto:** planejamento e implementação inicial. O enquadramento dos pacotes foi conferido com o código atual do cliente `minecraft-web-client`. A compatibilidade ponta a ponta com um cliente real e uma implantação LiveKit ainda precisa ser validada.

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

Formato esperado de `VoiceConfig`, compatível com a interface do cliente:

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

O plugin também confere as claims do token JWT recebidas: identidade, sala e validade temporal. Essa inspeção valida a resposta do backend, mas não substitui a validação criptográfica da assinatura, que continua sendo responsabilidade do LiveKit e do backend confiável.

## Configuração

A implementação utiliza `config.yml` para configurações específicas do servidor. Segredos devem ser fornecidos por variáveis de ambiente ou outro mecanismo seguro, nunca enviados ao Git.

Variável de ambiente padrão para a credencial do backend: `MCRAFT_VOICE_BACKEND_TOKEN`.

Consulte o arquivo `src/main/resources/config.yml` para ver todas as opções disponíveis.

## Comandos e permissões

- `/mcraftvoice status`: exibe o número de jogadores conectados à sessão de voz.
- `/mcraftvoice mute <jogador>`: adiciona o jogador à lista de identidades forçadas ao estado de mudo enviada aos clientes próximos.
- `/mcraftvoice unmute <jogador>`: remove o jogador dessa lista.

Permissões:

- `mcraftvoice.use`: permite ao jogador ativar o voice chat (padrão: todos).
- `mcraftvoice.moderate`: permite executar ações de moderação (padrão: operadores).

**Limite importante:** o comando `mute` controla a reprodução do participante nos clientes compatíveis por meio do campo `forceMuted`. Ele não revoga, por si só, a permissão de publicação de áudio no LiveKit. Uma restrição de publicação efetivamente aplicada no servidor exigirá integração administrativa adicional com o backend/LiveKit.

## Compatibilidade e requisitos

- Plataforma: Paper (alvo inicial).
- API Minecraft: 1.21.11.
- Java: 21.
- Cliente: versão do Minecraft Web Client que contenha a implementação de `minecraft-web-client:voice-chat`.
- Transporte de voz: LiveKit via WebRTC; o proxy Minecraft não funciona como retransmissor de áudio.

Velocity e coordenação de múltiplos servidores backend não fazem parte do escopo inicial.

## Desenvolvimento

O projeto utiliza Gradle e a toolchain Java definida no build.

```bash
gradle test
gradle build
```

O JAR gerado fica em `build/libs/`.

## Licença

Este projeto é distribuído sob a licença MIT. Consulte o arquivo [LICENSE](LICENSE).
