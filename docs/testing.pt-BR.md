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
- Silencie um jogador offline, reinicie o Paper e confirme que a política persistida é aplicada quando ele reconectar.
- Altere o estado de mute enquanto a emissão de token estiver pendente; o plugin deve descartar credenciais com permissões antigas e solicitar um novo token.
- Faça o endpoint retornar status diferente de 2xx ou expirar; o plugin não deve alterar o estado local nem informar sucesso.

## Respostas inválidas e segurança

- Rejeite JSON malformado, sala ou UUID inesperados, algoritmo JWT não suportado, permissões ausentes, tokens expirados ou com validade excessiva.
- Rejeite URLs LiveKit que não usem WSS, exceto WS em loopback durante desenvolvimento local.
- Confirme que redirecionamentos do backend não são seguidos.
- Confirme que os logs nunca contêm Bearer token do backend, segredo da API LiveKit ou JWT de participante.
- Confirme que `VoiceCredentials.toString()` oculta o JWT.
- Teste separadamente a revogação de tokens no LiveKit Cloud e self-hosted. O LiveKit self-hosted não invalida tokens já emitidos quando as permissões mudam; tokens antigos podem continuar utilizáveis até expirar.

## Evidências a registrar

Registre build do Paper, commit do plugin, versão/commit do Minecraft Web Client, build do backend, tipo de implantação LiveKit, sala de teste, data e resultados. Não anexe credenciais reais, JWTs, dados pessoais de jogadores ou logs de produção em issues públicas.
