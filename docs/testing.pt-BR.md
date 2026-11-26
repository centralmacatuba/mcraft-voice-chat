# Testes automatizados e plano manual de integração

## Testes automatizados do ciclo de vida Paper

A suíte Gradle inclui testes de integração com MockBukkit. Eles iniciam um servidor Paper simulado, carregam o plugin real a partir do descritor e dos recursos, verificam que o plugin permanece ativo quando a configuração opcional do backend está ausente e executam `/mcraftvoice status` e o apelido `/mcvoice`. Esses testes são executados na CI por `./gradlew test`.

MockBukkit não é um servidor Paper real e não implementa o Minecraft Web Client, um backend de tokens de produção nem LiveKit/WebRTC. A aprovação desses testes comprova somente o ciclo de vida e o registro de comandos cobertos. Use o plano ponta a ponta abaixo para validar interoperabilidade real.

# Plano de testes manuais de integração

Esta lista descreve testes que exigem um servidor Paper real, uma versão compatível do Minecraft Web Client, o backend confiável e uma sala LiveKit não produtiva. Testes unitários aprovados não comprovam compatibilidade ponta a ponta.

## Ambiente de teste

- Paper 1.21.11 e Java 21.
- Plugin compilado com `./gradlew clean test build`.
- Uma versão do cliente que implemente `minecraft-web-client:voice-chat`; registre a versão ou commit exato.
- Backend que implemente o [contrato de tokens e moderação](backend-contract.pt-BR.md).
- Sala LiveKit dedicada a testes.
- Conta de teste com `mcraftvoice.use` e conta operadora com `mcraftvoice.moderate`.

## Protocolo e ciclo de vida

- Entre no servidor e confirme o envio de `available` após o cliente registrar o canal.
- Ative o voice chat e confirme que `config` contém URL WSS, sala, UUID do jogador, alcance de audição e token temporário esperados.
- Confirme que o cliente entra na sala LiveKit correta e que o áudio trafega por WebRTC, não pelas mensagens de plugin do Minecraft.
- Desative o voice chat e confirme que o servidor remove a sessão ativa.
- Desconecte enquanto a emissão do token estiver pendente; uma resposta tardia não deve criar uma sessão ativa.
- Reconecte e confirme que o jogador pode solicitar uma nova sessão.
- Remova `mcraftvoice.use` de um jogador com sessão ativa; confirme que o servidor remove a sessão local na próxima atualização de proximidade e não envia novas atualizações `nearby` para esse jogador. O protocolo upstream atual não possui uma ação `disable` do servidor para o cliente; isso, por si só, não desconecta um participante já conectado ao LiveKit.
- Remova `mcraftvoice.use` enquanto a emissão do token estiver pendente; confirme que a solicitação é invalidada e uma resposta tardia não cria uma sessão.

## Proximidade

- Dois jogadores no mesmo mundo e dentro do alcance configurado aparecem em `nearby.players` um do outro.
- Jogadores fora do alcance ou em mundos diferentes não aparecem.
- Cruzar o limite de distância atualiza a lista.
- Sair do servidor remove o jogador das listas seguintes.
- Dados de proximidade inalterados não são retransmitidos repetidamente.

## Moderação

- Configure o endpoint e confirme que `/mcraftvoice status` informa que ele está configurado.
- Silencie um jogador conectado. O backend deve persistir a política, revogar a permissão de publicação do participante LiveKit e interromper as faixas de áudio já publicadas.
- Confirme que o UUID silenciado aparece nas listas `nearby.forceMuted` dos demais ouvintes.
- Confirme que um token solicitado após o mute contém `video.canPublish=false`.
- Remova o mute e confirme que o backend restaura a permissão de publicação pretendida.
- Silencie um jogador offline cuja identidade esteja no cache do Paper (o jogador precisa ter entrado ao menos uma vez); reinicie o Paper e confirme que a política persistida é aplicada quando ele reconectar.
- Envie mute e depois unmute (e também unmute e depois mute) rapidamente para o mesmo UUID. Confirme que cada requisição contém uma revisão persistida maior e um ID de operação único. Simule timeout enquanto a requisição antiga ainda está em processamento; o backend deve rejeitar revisões obsoletas atomicamente, mesmo se a requisição antiga chegar depois de uma nova. A fila local apenas serializa o envio local.
- Envie solicitações para dois UUIDs diferentes; confirme que elas não são serializadas desnecessariamente entre si.
- Altere o estado de mute enquanto a emissão de token estiver pendente; o plugin deve descartar credenciais com permissões antigas e solicitar um novo token.
- Faça o endpoint retornar status diferente de 2xx ou expirar; o plugin não deve alterar o estado local nem informar sucesso.

## Respostas inválidas e segurança

- Rejeite JSON malformado, sala ou UUID inesperados, algoritmo JWT não suportado, permissões ausentes, tokens expirados ou com validade excessiva.
- Rejeite tokens sem `nbf`, com `exp` ou `nbf` fracionário/não inteiro, com `exp <= nbf`, com validade total `exp - nbf` acima do máximo configurado, ou com `nbf` mais de 60 segundos no futuro.
- Rejeite corpos de resposta do backend de tokens maiores que 64 KiB cancelando a assinatura do corpo. Simule um corpo que trava após os cabeçalhos; confirme o cancelamento no prazo total da requisição e que nenhuma credencial seja entregue após o timeout.
- Rejeite todas as URLs `ws://` por padrão; confirme que `ws://` em loopback só funciona com `allow-insecure-loopback: true` e que `ws://` remoto continua rejeitado mesmo com essa opção habilitada.
- Confirme que redirecionamentos do backend não são seguidos.
- Envie mensagens de plugin malformadas repetidamente; confirme que os avisos nos logs têm limite de frequência por jogador.
- Envie ações desconhecidas com caracteres de controle; confirme que são sanitizadas antes de entrar nos logs.
- Confirme que os logs nunca contêm Bearer token do backend, segredo da API LiveKit ou JWT de participante.
- Confirme que `VoiceCredentials.toString()` oculta o JWT.
- Mantenha um cliente conectado tempo suficiente para receber um token LiveKit renovado pelo servidor; depois simule uma interrupção de rede e reconecte, registrando se a credencial renovada é utilizada.
- No LiveKit Cloud, remova um participante/altere permissões e verifique o limite de revogação `nbf`, a tolerância padrão de um minuto para diferença de relógio e a janela de revogação de 10 minutos. Teste `revoke_token_ts` explícito se o backend o utilizar.
- No LiveKit self-hosted, confirme que remover um participante não revoga tokens de reconexão já emitidos/renovados; confirme que o controle de admissão do backend bloqueia novas emissões e reconexões conforme necessário.

## Evidências a registrar

Registre build do Paper, commit do plugin, versão/commit do Minecraft Web Client, build do backend, tipo de implantação LiveKit, sala de teste, data e resultados. Não anexe credenciais reais, JWTs, dados pessoais de jogadores ou logs de produção em issues públicas.
