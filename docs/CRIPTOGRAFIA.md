# Como funciona a criptografia no Nodus

Este documento explica, em termos que não exigem conhecimento técnico
prévio, como o Nodus protege suas notas — e o que isso significa na
prática pro seu dia a dia (principalmente sobre senha esquecida, que é a
parte mais importante de entender *antes* de precisar dela).

## A ideia central

Sua senha nunca é o que protege as notas diretamente. Em vez disso, ela é
usada pra *calcular* uma chave de criptografia — um código bem mais longo
e complexo do que qualquer senha memorizável. É essa chave calculada que
realmente cifra e decifra seus dados.

Isso é assim de propósito: significa que a senha em si **não precisa
ficar guardada em lugar nenhum**, nem cifrada. Sem ela guardada em algum
lugar, não tem como alguém — nem nós, nem um invasor com acesso ao
aparelho — extrair a senha de dentro do app.

## Os dois algoritmos usados

- **Argon2id**, para transformar sua senha na chave de criptografia. Esse
  é o algoritmo recomendado atualmente para esse tipo de tarefa — ele é
  de propósito lento e pesado (usa bastante memória), o que é bom aqui:
  torna inviável tentar "adivinhar" milhões de senhas por segundo.
- **AES-256-GCM**, pra cifrar o conteúdo com a chave calculada. É o
  mesmo tipo de criptografia usado por bancos, mensageiros e gerenciadores
  de senha — autenticada, ou seja, além de esconder o conteúdo, ela
  também detecta sozinha se o arquivo foi corrompido ou adulterado.

## O que é criptografado

Praticamente tudo que você escreve:

- Título e conteúdo de cada nota, com toda a formatação.
- Nome, cor e capa de cada caderno.
- Backups exportados (o arquivo inteiro sai do aparelho já cifrado).

## O que NÃO é criptografado

- Fotos de capa escolhidas da sua galeria (ficam salvas como imagem comum,
  dentro da área privada do app).
- Preferências de aparência do app (tema, cor, layout) — não são
  informação sensível sobre o conteúdo das suas notas.

Veja a [Política de Privacidade](POLITICA_DE_PRIVACIDADE.md) pra lista
completa.

## Recuperação de senha: por que não existe

Essa é a parte mais importante deste documento.

**Se você esquecer sua senha, não existe nenhuma forma de recuperar suas
notas.** Nem nós, nem ninguém.

Isso não é uma limitação técnica que algum dia vai ser resolvida — é uma
consequência direta de como a segurança funciona aqui. Como a chave de
criptografia é *calculada* a partir da senha (e a senha em si nunca fica
guardada), sem a senha certa não tem como recalcular a chave certa. Um
sistema de "recuperar senha" só é possível quando existe algum outro jeito
de acessar os dados sem a senha original — e é exatamente esse "outro
jeito" que tornaria os seus dados menos seguros. A ausência de
recuperação **é** a segurança, não um efeito colateral dela.

**Por isso:** guarde sua senha em um lugar seguro e confiável (um
gerenciador de senhas, por exemplo). Trocar de senha dentro do app (em
Configurações → Segurança → Trocar senha) também exige a senha atual — não
existe um jeito de "resetar" sem ela.

## E o backup, ajuda nesse caso?

Não. O arquivo de backup também é cifrado com a mesma senha — se você
perdeu a senha, o backup também fica ilegível. O backup protege contra
perder o *aparelho*, não contra esquecer a senha.
