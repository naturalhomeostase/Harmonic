# Perguntas frequentes — Nodus

## Segurança e senha

**Esqueci minha senha. Como recupero minhas notas?**
Não é possível. Veja [Como funciona a criptografia](CRIPTOGRAFIA.md#recuperação-de-senha-por-que-não-existe)
pra entender por quê — e, principalmente, guarde sua senha num lugar
seguro *antes* de precisar dela.

**Posso trocar minha senha depois?**
Sim — em Configurações → Segurança → Trocar senha. Você precisa saber a
senha atual pra trocar (não existe reset sem ela). Todas as suas notas são
re-cifradas automaticamente com a senha nova; não feche o app enquanto
isso estiver acontecendo.

**O que acontece se eu errar a senha várias vezes?**
Depois de algumas tentativas erradas seguidas, o app bloqueia novas
tentativas por um tempo — é uma proteção contra alguém tentando adivinhar
sua senha por tentativa e erro.

**Minha biometria (digital/rosto) substitui a senha?**
Não totalmente — ela é um atalho pra desbloquear mais rápido, mas a senha
continua sendo pedida também (a biometria não *é* a chave de criptografia,
só evita digitar a senha toda vez).

**Posso configurar quanto tempo o app demora pra trancar sozinho?**
Sim — em Configurações → Segurança → "Bloquear automaticamente", com
opções de imediato, 30s, 1 min ou 5 min.

## Backup e restauração

**Como faço backup das minhas notas?**
Em Configurações (ou na tela inicial, dependendo de onde o botão estiver
posicionado no momento), escolha "Fazer backup". O Nodus cifra tudo e abre
o seletor "Salvar como..." do Android — você escolhe onde guardar
(Google Drive, outro serviço, ou local).

**Preciso fazer login no Google pra fazer backup no Drive?**
Não. O Nodus usa o seletor nativo do Android, o mesmo que aparece quando
você salva uma foto ou um PDF de qualquer app — ele já mostra o Google
Drive como opção se o app do Drive estiver instalado, sem exigir login
dentro do Nodus.

**Meu backup falhou ao restaurar. O que aconteceu?**
O Nodus tenta ser específico sobre isso:
- *"Não consegui abrir esse backup"* → ou é de outra senha, ou o arquivo
  está corrompido/incompleto.
- *"Esse arquivo não parece ser um backup do Nodus"* → foi escolhido um
  arquivo que não é um backup válido.
- *"Backup de uma versão mais nova"* → atualize o app antes de tentar de
  novo.

**Restaurar um backup apaga o que já está no aparelho?**
Sim — restaurar substitui os dados atuais pelos do backup. O app avisa
antes de confirmar essa ação.

## Notas e organização

**Qual a diferença entre "Arquivar" e "Mover pra lixeira"?**
Arquivar tira a nota da lista do caderno, mas sem prazo de validade — ela
fica guardada até você decidir desarquivar, e continua aparecendo na
busca. Mover pra lixeira é o primeiro passo pra exclusão definitiva.

**Notas na lixeira ficam lá pra sempre?**
Não — a lixeira existe pra dar uma segunda chance em caso de exclusão
por engano, mas notas antigas na lixeira são removidas definitivamente
depois de um tempo.

**Dá pra colocar link em uma nota?**
Sim, pelo botão de link na barra de formatação do editor.

## Sobre o app em geral

**O Nodus tem anúncios ou coleta dados de uso?**
Não, nenhum dos dois. Veja a [Política de Privacidade](POLITICA_DE_PRIVACIDADE.md)
completa.

**O Nodus funciona sem internet?**
Sim, o app inteiro funciona 100% offline — a única vez que ele toca a
internet é se você mesmo escolher salvar um backup num serviço de nuvem.

**Onde posso sugerir algo ou relatar um problema?**
[PREENCHER — link do GitHub, e-mail de suporte, etc.]
