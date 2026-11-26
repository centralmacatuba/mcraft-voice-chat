# Contrato do backend de voz

Este documento define o contrato servidor-a-servidor consumido pelo plugin Paper. O backend é um serviço separado e não é implementado nem hospedado por este repositório.

## Requisitos de segurança

- Disponibilize os endpoints exclusivamente por HTTPS.
- Autentique o servidor Paper com o Bearer token configurado. Use uma credencial forte e exclusiva por instalação.
- Nunca retorne o segredo da API LiveKit.
- Não confie em clientes públicos para escolher identidades, salas ou IDs de servidor arbitrários.
- Aplique limites de requisições e registre alterações administrativas de moderação.
- Nunca registre cabeçalhos Authorization, JWTs ou corpos completos de resposta.
- Não redirecione requisições para outro host.

## Endpoint de tokens

**Requisição:** `POST <token-provider.endpoint>`

Cabeçalhos: `Authorization: Bearer <credencial-do-servidor>`, `Content-Type: application/json`, `Accept: application/json`.

Corpo:

```json
{
  "playerUuid": "00000000-0000-0000-0000-000000000000",
  "username": "Jogador",
  "serverId": "mcraft",
  "room": "minecraft-survival",
  "forceMuted": false
}
```

Resposta:

```json
{
  "url": "wss://livekit.example.net",
  "room": "minecraft-survival",
  "token": "<JWT temporário do participante LiveKit>"
}
```

O campo `forceMuted` da requisição é apenas uma indicação adicional de bloqueio enviada pelo Paper. O backend deve consultar sua própria política persistida e nunca permitir que `forceMuted=false` sobrescreva um mute armazenado. Calcule o mute efetivo como a política persistida OU a indicação recebida. Portanto, o backend pode retornar `canPublish=false` mesmo quando a indicação local é falsa. O plugin rejeita tokens que concedam publicação quando a indicação local é verdadeira, mas aceita uma restrição mais rigorosa aplicada pelo backend.

O JWT deve usar HS256; definir `sub` como o UUID Minecraft exato e `video.room` como a sala solicitada; conceder `video.roomJoin=true` e `video.canSubscribe=true`; definir `video.canPublish=true` somente quando a política efetiva permitir publicação; e expirar dentro de `max-token-lifetime-seconds` (mantenha esse valor em até 600 segundos). A claim `exp` deve ser um NumericDate inteiro. `nbf` é obrigatório para compatibilidade com a revogação de tokens do LiveKit. Deve ser um NumericDate inteiro e não pode estar mais de 60 segundos no futuro, considerando a diferença de relógio entre sistemas.

O LiveKit renova os tokens de acesso de clientes conectados; portanto, um cliente ativo pode possuir um token renovado pelo servidor, e não apenas o token original de entrada. No LiveKit Cloud, alterações de permissão e remoção de participantes revogam tokens usando o timestamp `nbf`; o limite padrão inclui uma tolerância de um minuto para diferenças de relógio, e a revogação permanece efetiva por 10 minutos. No LiveKit self-hosted, alterações de permissão afetam participantes conectados e revogam a publicação (o que remove as faixas já publicadas), mas não invalidam tokens já emitidos ou renovados para futuras reconexões. Portanto, um TTL inicial curto, sozinho, não garante revogação imediata. Aplique a política de admissão no backend e não emita novos tokens depois que o acesso for revogado.

O plugin decodifica e valida as claims, mas não verifica criptograficamente a assinatura do JWT. A assinatura e sua confiança continuam sob responsabilidade do backend confiável e do LiveKit.

## Endpoint de moderação

**Requisição:** `POST <voice-chat.moderation.endpoint>`

Use os mesmos cabeçalhos de autenticação e conteúdo do endpoint de tokens.

Corpo:

```json
{
  "playerUuid": "00000000-0000-0000-0000-000000000000",
  "serverId": "mcraft",
  "room": "minecraft-survival",
  "muted": true
}
```

Retorne status 2xx somente depois que a política de mute estiver persistida e a operação LiveKit tiver sido aceita. Retorne status diferente de 2xx se não puder aplicá-la; nesse caso, o plugin não altera o estado local.

O backend deve:
1. validar se o servidor autenticado pode administrar o ID de servidor e a sala informados;
2. persistir a política por servidor, sala e UUID do participante;
3. atualizar participantes conectados para `canPublish=false` quando silenciados e restaurar a permissão pretendida quando liberados;
4. aplicar a política persistida a tokens futuros, inclusive após reconexões;
5. tornar requisições repetidas idempotentes.

Uma indicação `forceMuted` enviada ao cliente não é aplicação efetiva. A permissão do participante no LiveKit é a fronteira de aplicação. Se o participante estiver offline, persista a política e garanta que o próximo token seja emitido sem permissão de publicação.

Atualmente, o plugin invalida o estado local de sessão ativa/pendente quando `mcraftvoice.use` é revogada, mas o protocolo upstream não possui uma ação `disable` do servidor para o cliente e este repositório ainda não chama uma operação do backend para remover o participante/controlar sua admissão nesse evento. Portanto, um participante já conectado ao LiveKit pode continuar conectado até que o cliente se desconecte ou a sessão termine. Não considere a revalidação local da permissão como revogação remota imediata.

## Operação

Use uma credencial de backend por servidor Paper, mantenha as credenciais da API LiveKit somente no armazenamento seguro do backend, restrinja os privilégios administrativos às salas necessárias e teste emissão de tokens, mute, unmute, reconexão e reinicialização em uma sala não produtiva.
