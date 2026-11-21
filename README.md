# AutoBots Automanager (ATVIV)

[![CI](https://github.com/YosephLima/AV4-DW3/actions/workflows/ci.yml/badge.svg)](https://github.com/YosephLima/AV4-DW3/actions/workflows/ci.yml)

"Atualização de segurança" do automanager: a API da ATVIII passa a exigir **autenticação e autorização via JSON Web Token (JWT)**. O usuário faz login com nome de usuário e senha, recebe um token assinado e o envia em cada requisição. Cada perfil (administrador, gerente, vendedor e cliente) só faz o que a Tabela 1 do enunciado permite.

Java 17, Spring Boot 2.6 (Web, Data JPA, HATEOAS, Validation e Security), JJWT 0.11.5 e banco H2 em memória.

Atividade ATVIV da disciplina Desenvolvimento Web III (DSM, Fatec), prof. Gerson Penha.

## Como as funcionalidades pedidas foram atendidas

| Funcionalidade | Onde está |
| -------------- | --------- |
| Autenticação via JWT | `POST /login` devolve o token, que vai no cabeçalho `Authorization: Bearer <token>` das demais requisições |
| Perfis Administrador, Gerente, Vendedor e Cliente | campo `perfis` do usuário: `ADMINISTRADOR`, `GERENTE`, `VENDEDOR`, `CLIENTE` (e `FORNECEDOR`, que não faz login) |
| Autorizações da Tabela 1 | [Perfis e autorizações](#perfis-e-autorizações) |
| Senhas protegidas | gravadas com BCrypt e nunca devolvidas nas respostas |
| Base da ATVIII | CRUD de empresas, usuários, veículos, mercadorias, serviços, vendas e credenciais com HATEOAS, descrito a partir de [Como os recursos se relacionam](#como-os-recursos-se-relacionam) |

## Executando

O banco é em memória: os dados somem ao encerrar a aplicação, e a cada inicialização o seed recria uma empresa com um usuário de cada perfil, fornecedor, veículo, catálogo e duas vendas.

### Com Java

Requer o JDK 17 ou superior (confira com `java -version`). O Maven não precisa estar instalado: o wrapper `mvnw` acompanha o projeto e baixa o que falta na primeira execução.

```bash
cd automanager
./mvnw spring-boot:run        # Linux/macOS (se faltar permissão: chmod +x mvnw)
mvnw.cmd spring-boot:run      # Windows
```

### Com Docker

Só o Docker é necessário: a imagem compila o projeto num primeiro estágio e roda só com o JRE no segundo.

```bash
docker build -t automanager automanager
docker run --rm -p 8080:8080 -e JWT_SECRET="um-segredo-qualquer-com-pelo-menos-sessenta-e-quatro-bytes-de-tamanho" automanager
```

Nos dois casos a API fica em `http://localhost:8080`.

O segredo que assina os tokens vem da variável `JWT_SECRET` e precisa ter ao menos 64 bytes (exigência do HS512): a aplicação não sobe com um segredo menor. Sem a variável, vale um segredo de desenvolvimento definido no `application.properties`, que só serve para rodar localmente. A validade do token é de 1 hora e pode ser trocada pela variável `JWT_EXPIRATION`, em milissegundos.

### Usuários do seed

Todos com a senha `123456`:

| Id | Usuário | Perfil | Nome de usuário |
| -- | ------- | ------ | --------------- |
| 1 | Dom Pedro | `VENDEDOR` | `dompedrovendedor` |
| 2 | Componentes varejo de partes automotivas | `FORNECEDOR` | `dompedrofornecedor` (não faz login) |
| 3 | Dom pedro cliente | `CLIENTE` | `dompedrocliente` |
| 4 | Administrador do sistema | `ADMINISTRADOR` | `admin` |
| 5 | Gerente da loja | `GERENTE` | `gerente` |

As duas vendas do seed foram feitas pelo vendedor 1 para o cliente 3.

### Fazendo login

```bash
curl -i -X POST localhost:8080/login -H 'Content-Type: application/json' \
  -d '{"nomeUsuario":"admin","senha":"123456"}'
```

O token volta no cabeçalho `Authorization`, como no modelo do professor, e também no corpo, com a data de expiração, os perfis e o link para o usuário autenticado:

```json
{
  "tipo": "Bearer",
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "expiracao": "2026-11-21T17:47:42.000+00:00",
  "perfis": ["ADMINISTRADOR"],
  "_links": { "usuario": { "href": "http://localhost:8080/usuarios/4" } }
}
```

Nas demais requisições, envie o token no cabeçalho (no Postman ou Insomnia, aba Authorization, tipo Bearer Token):

```bash
TOKEN=eyJhbGciOiJIUzUxMiJ9...
curl localhost:8080/empresas/1 -H "Authorization: Bearer $TOKEN"
```

Um bom ponto de partida é `GET /empresas/1`: a partir dos links dela se chega a todo o resto.

## Perfis e autorizações

A Tabela 1 do enunciado aplicada aos recursos da API:

| Recurso | Administrador | Gerente | Vendedor | Cliente |
| ------- | ------------- | ------- | -------- | ------- |
| Usuários administradores e fornecedores | CRUD | não | não | não |
| Usuários gerentes e vendedores | CRUD | CRUD | lê só o próprio cadastro | não |
| Usuários clientes | CRUD | CRUD | CRUD | lê só o próprio cadastro |
| Dados do usuário: telefones, documentos, emails, endereço, credenciais e veículos | CRUD | seguem a regra do usuário dono | seguem a regra do usuário dono | lê os próprios |
| Empresa, com seus telefones e endereço | CRUD | lê | lê | não |
| Mercadorias e serviços (e o vínculo de fornecedor) | CRUD | CRUD | lê | não |
| Vendas | CRUD | CRUD | cria as que ele faz e lê essas | lê as que comprou |
| Listas gerais de telefones, documentos, emails, endereços e credenciais | lê | não | não | não |

Regras que completam a tabela:
- **Ninguém promove além do que gerencia.** Ao cadastrar ou alterar um usuário, os perfis atribuídos também precisam ser gerenciáveis por quem faz a operação: o gerente não cria um administrador, e o vendedor não transforma um cliente em vendedor ou fornecedor.
- **Usuário com vários perfis** é protegido pelo mais alto: o gerente não altera quem é `GERENTE` e `ADMINISTRADOR` ao mesmo tempo. Quem faz login com vários perfis soma as permissões de todos.
- **Venda do vendedor:** o `funcionarioId` precisa ser o próprio vendedor. O funcionário de qualquer venda precisa ter o perfil `VENDEDOR`, `GERENTE` ou `ADMINISTRADOR`, e o cliente, o perfil `CLIENTE`.
- **Listagens filtradas:** `GET /usuarios`, `/vendas`, `/veiculos` e as coleções de empresa, usuário e veículo trazem só o que o perfil pode ler. O cliente que lista `/usuarios` recebe só a si mesmo.
- **Fornecedor não faz login.** É um papel de negócio (quem fornece mercadorias), fora da Tabela 1, e só o administrador gerencia esses usuários.

### Respostas 401 e 403

| Situação | Status |
| -------- | ------ |
| Requisição sem token, com token inválido, adulterado ou expirado | `401` |
| Login com senha errada, usuário inexistente, credencial inativa ou usuário sem perfil de acesso | `401` |
| Token de um usuário cuja credencial foi inativada ou que perdeu os perfis de acesso depois do login | `401` |
| Perfil sem permissão para a rota ou para aquele registro | `403` |
| Id inexistente consultado por quem não enxergaria o registro | `403` (não revela se o id existe) |

As duas respostas usam o mesmo corpo de erro das demais:

```json
{ "status": 403, "error": "Forbidden", "message": "Acesso negado: o perfil do usuário autenticado não permite esta operação", "path": "/usuarios/4" }
```

## Como a segurança foi implementada

A estrutura segue o modelo de projeto do professor (`atviv-autobots-microservico-spring`):

| Parte | Arquivo |
| ----- | ------- |
| Configuração do Spring Security: sem sessão, todas as rotas autenticadas e regras por método | `configuracao/Seguranca` |
| Codificador de senhas (BCrypt) | `configuracao/ConfiguracaoSenha` |
| Respostas 401 e 403 | `configuracao/RespostasSeguranca` |
| Geração, leitura e validação do token | `jwt/ProvedorJwt`, `GeradorJwt`, `AnalisadorJwt`, `ValidadorJwt` |
| Login em `POST /login` | `filtros/Autenticador` |
| Leitura do cabeçalho `Authorization` em cada requisição | `filtros/Autorizador`, `AutenticadorJwt`, `ValidadorCabecalho`, `AnalisadorCabecalho` |
| Usuário autenticado | `adaptadores/UserDetailsImpl`, `UserDetailsServiceImpl` |
| Perfis exigidos por rota | `@PreAuthorize` nos controles, com as expressões de `autorizacao/Perfis` |
| Regras que dependem do registro (dono, perfis do alvo, participantes da venda) | `autorizacao/PoliticaAcesso`, chamada como `@acesso` nos `@PreAuthorize` |

### Escolhas em relação ao modelo do professor

| Ponto | Modelo | Aqui | Motivo |
| ----- | ------ | ---- | ------ |
| Perfis | `ROLE_ADMIN`, `ROLE_GERENTE`... num enum próprio | `ADMINISTRADOR`, `GERENTE`, `VENDEDOR`, `CLIENTE` no `PerfilUsuario` da ATVIII | Nomes de negócio no cadastro e nas respostas. O prefixo `ROLE_` só existe nas autoridades do Spring Security |
| Biblioteca JWT | JJWT 0.9.1 | JJWT 0.11.5 | A 0.9.1 depende de `javax.xml.bind`, que não existe mais no Java 17 |
| Segredo do token | Fixo no `application.properties`, com 20 caracteres | Variável `JWT_SECRET`, com ao menos 64 bytes, conferidos ao subir | O HS512 exige chave de 512 bits, e o segredo não deve ficar no código |
| Credenciais | Uma por usuário | As credenciais da ATVIII; o login usa a de usuário e senha | Mantém o modelo da ATVIII, em que o usuário também pode ter código de barras |
| Busca no login | `findAll()` e laço sobre todos os usuários | Consulta pelo nome de usuário | Uma consulta só, que não cresce com a base |
| Resposta do login | Token só no cabeçalho | Token no cabeçalho e no corpo, com o link do usuário | O cliente da API não precisa ler cabeçalhos, e a resposta mantém o nível 3 do RMM |

Outras decisões:
- **O usuário é recarregado a cada requisição.** O token carrega só o nome de usuário, e os perfis vêm do banco. Por isso perfis alterados e credenciais inativadas valem na hora, sem esperar o token expirar. O custo é uma consulta por requisição, aceitável aqui.
- **Campos da credencial que ganharam uso:** `inativo` bloqueia o login e os tokens já emitidos, e `ultimoAcesso` é preenchido a cada login.
- **Console do H2 desligado:** ele não passa pela autenticação JWT e exporia o banco inteiro.
- **Os links HATEOAS não são filtrados por perfil.** Eles descrevem o recurso, e a permissão é conferida quando o link é seguido. Um vendedor vê, por exemplo, o link `edit` de uma venda, mas recebe `403` ao usar.

### Mudança em relação ao diagrama da ATVIII

O diagrama tem `PerfilUsuario { CLIENTE, FUNCIONARIO, FORNECEDOR }`. A Tabela 1 divide o funcionário em administrador, gerente e vendedor, então o enum passou a ser `{ ADMINISTRADOR, GERENTE, VENDEDOR, CLIENTE, FORNECEDOR }`. O valor `FUNCIONARIO` deixou de existir e é recusado com `400`. O papel `funcionario` da venda continua com esse nome, agora preenchido por qualquer um dos três perfis de funcionário. O resto do diagrama não mudou.

## Testes e integração contínua

```bash
cd automanager
./mvnw test
```

São 106 testes, que rodam em H2 sem precisar da aplicação no ar:
- `TabelaPerfisTests`: 84 casos da Tabela 1. Cada um faz login com um perfil, chama uma rota e confere o status (`200`, `201`, `204`, `403` ou `404`);
- `AutenticacaoTests`: login, tokens inválidos, adulterados e expirados, credencial inativada, perfil alterado, senha gravada com BCrypt e listagens filtradas;
- `AutomanagerApplicationTests`: a aplicação sobe.

### Roteiro de verificação

O `testes/roteiro.sh` percorre a API no ar com `curl` e confere o status de 135 requisições. A primeira parte repete o CRUD da ATVIII com o token do administrador. A segunda faz login com cada perfil e percorre a Tabela 1. Os ids partem do seed, então ele deve rodar com a aplicação recém-iniciada (em bash: Linux, macOS, Git Bash ou WSL):

```bash
./testes/roteiro.sh                          # API em http://localhost:8080
B=http://localhost:8081 ./testes/roteiro.sh  # se a API estiver em outra porta
```

Cada linha mostra o resultado e, nos erros, a mensagem devolvida pela API. A última linha resume, por exemplo `135 de 135 verificações passaram`, e o script termina com erro se alguma falhar.

### CI no GitHub Actions

O workflow `.github/workflows/ci.yml` roda a cada push na `main`, a cada pull request e sob demanda, em dois jobs:

1. **Build e testes:** JDK 17 (Temurin) com cache do Maven e `./mvnw verify`. Os relatórios dos testes ficam guardados por 7 dias como artefato da execução.
2. **Imagem Docker e roteiro de verificação** (só roda se o primeiro passar): constrói a imagem, sobe o container com um segredo JWT aleatório e roda o `roteiro.sh` contra ele. Assim o CI confere tanto o código quanto a imagem funcionando de ponta a ponta. A imagem não é publicada em nenhum registro.

## Como os recursos se relacionam

A regra da ATVII continua valendo: **criar** é uma ação sobre o dono do recurso, então a rota fica aninhada nele; **consultar, alterar e excluir** é uma ação sobre o próprio recurso, na URI dele.

| Recurso | Dono (onde é criado) | URI própria |
| ------- | -------------------- | ----------- |
| Usuário, mercadoria, serviço, venda | empresa: `POST /empresas/{id}/usuarios`, `/mercadorias`, `/servicos`, `/vendas` | `/usuarios/{id}`, `/mercadorias/{id}`... |
| Veículo, documento, email, credencial | usuário: `POST /usuarios/{id}/veiculos`, `/documentos`, `/emails`, `/credenciais` | `/veiculos/{id}`, `/documentos/{id}`... |
| Telefone | usuário ou empresa: `POST /usuarios/{id}/telefones` ou `/empresas/{id}/telefones` | `/telefones/{id}` |
| Endereço | usuário ou empresa: `POST /usuarios/{id}/endereco` ou `/empresas/{id}/endereco` (singular, no máximo um) | `/enderecos/{id}` |

As coleções grandes não vêm embutidas no JSON do dono. Elas têm URI própria, que aceita `GET` para listar e, quando o recurso é criado ali, `POST` para cadastrar: `/empresas/{id}/usuarios`, `/empresas/{id}/mercadorias`, `/empresas/{id}/servicos`, `/empresas/{id}/vendas`, `/usuarios/{id}/veiculos`, `/usuarios/{id}/vendas` (como cliente ou funcionário), `/usuarios/{id}/mercadorias` (fornecidas) e `/veiculos/{id}/vendas`. Telefones, documentos, emails, credenciais e endereço são pequenos e vêm embutidos no usuário ou na empresa.

## Decisões de projeto da ATVIII

### Correções no modelo recebido

O código base trazia as entidades do diagrama, mas alguns mapeamentos impediam o próprio funcionamento do sistema. Rodando a base original com H2, o Hibernate gera:

```
alter table venda_mercadorias add constraint UK_9vcb8taxswouhu0pkldgao2us unique (mercadorias_id)
alter table venda_servicos add constraint UK_m0kokvdellhuom9bbivdul5kl unique (servicos_id)
```

Como `Venda.mercadorias` e `Venda.servicos` eram `@OneToMany`, uma mercadoria ou serviço do catálogo só podia aparecer em **uma** venda. Por isso o seed original criava um `alinhamento2` duplicado para a segunda venda. Essas e outras correções:

- **Itens da venda** viraram `@ManyToMany`. O seed agora reaproveita a mesma roda e o mesmo alinhamento nas duas vendas.
- **Venda e veículo**: `Venda.veiculo` era `@OneToOne` (um veículo, uma venda), contradizendo `Veiculo.vendas`, que ainda era mapeado em uma tabela própria. Agora é `@ManyToOne`, e `Veiculo.vendas` é o lado inverso (`mappedBy`): uma única FK representa a relação.
- **Usuário e veículo**: o mesmo problema entre `Usuario.veiculos` (tabela de junção) e `Veiculo.proprietario` (FK). `Usuario.veiculos` passou a ser o lado inverso.
- **Vendas do usuário**: `Usuario.vendas` repetia, em outra tabela, o que `Venda.cliente` e `Venda.funcionario` já dizem, e podia ficar dessincronizada. A coleção saiu da entidade e virou a consulta `GET /usuarios/{id}/vendas`.
- **Credencial abstrata**: sem informação de tipo, o JSON não consegue criar uma `Credencial`. O campo `tipo` (`usuario-senha` ou `codigo-barra`) diz qual subclasse instanciar.
- **Igualdade das entidades**: o `@Data` do Lombok gerava `equals`/`hashCode` sobre todos os campos, o que entra em laço nas referências bidirecionais e muda o `hashCode` de um objeto que já está dentro de um `Set`. As entidades agora herdam de `EntidadeBase`, com igualdade pelo id e `hashCode` fixo por classe. Isso também evita uma armadilha descoberta na ATVII: o `equals` do `RepresentationModel` compara só os links, então dois telefones recém-carregados seriam "iguais" e um deles sumiria do `Set`.
- **Números no PUT parcial**: `quantidade`, `valor`, `codigo` e `inativo` passaram de primitivos para `Long`, `Double` e `Boolean`. Sem isso, um campo ausente no corpo chegaria como `0` ou `false` e apagaria o valor atual.
- **Enums** gravados pelo nome (`EnumType.STRING`), não pela posição, e o campo `fabricao` corrigido para `fabricacao`. O pacote `entitades` foi corrigido para `entidades`.

### A venda recebe ids, não objetos

Uma venda referencia registros que já existem, então o corpo traz os ids deles:

```json
{ "identificacao": "VW-001", "clienteId": 6, "funcionarioId": 7, "veiculoId": 2, "mercadoriaIds": [2], "servicoIds": [4] }
```

Mandar objetos aninhados faria o JPA tentar gravá-los de novo. Na criação e na alteração, a venda é validada inteira:
- o cliente e o funcionário precisam ser usuários da empresa da venda, o cliente com o perfil `CLIENTE` e o funcionário com `VENDEDOR`, `GERENTE` ou `ADMINISTRADOR`;
- o veículo, se informado, precisa ser do cliente;
- as mercadorias e serviços precisam estar no catálogo da empresa;
- é preciso ao menos um item.

O valor total (`valorTotal`) não é gravado: ele é calculado a cada consulta, somando o preço atual dos itens. Por isso, se o valor de uma mercadoria ou serviço muda, o total das vendas antigas que o incluem muda junto (veja [O que ficou para a próxima etapa](#o-que-ficou-para-a-próxima-etapa)).

### Exclusões preservam o histórico de vendas

Excluir algo que aparece em uma venda apagaria parte do histórico, então essas exclusões respondem `409`: usuário que é cliente ou funcionário de alguma venda, veículo com vendas, mercadoria ou serviço vendido e empresa com vendas. As demais limpam os vínculos antes de apagar: a mercadoria sai da lista do fornecedor, o usuário sai da empresa e a empresa excluída apenas desvincula seus usuários, sem apagá-los.

### Links e erros (mesmas convenções da ATVII)

Os links usam as relações registradas na IANA (`IanaLinkRelations`): `self`, `collection`, `edit` (a URI que aceita `PUT` e `DELETE`) e `up` (o dono do recurso). Os nomes próprios ficam para o que a IANA não cobre:
- coleções relacionadas: `usuarios`, `mercadorias`, `servicos`, `vendas` e `veiculos`;
- participantes da venda: `cliente`, `funcionario` e `veiculo`;
- `fornecedor`, na mercadoria;
- cadastro de recursos embutidos: `cadastrar-telefone`, `cadastrar-documento` etc.

O link `cadastrar-endereco` só aparece enquanto o dono não tem endereço, e o link `mercadorias` do usuário só aparece se ele for fornecedor.

Os erros usam o corpo padrão do Spring Boot, com a mensagem preenchida pelas `ResponseStatusException` dos controles e pelo `TratadorErros` (validação, JSON inválido e restrições do banco):

```json
{ "status": 400, "error": "Bad Request", "message": "O veículo 1 não pertence ao cliente da venda", "path": "/empresas/2/vendas" }
```

Nos erros de validação, a mensagem lista cada campo inválido com o motivo, inclusive nos itens embutidos. Por exemplo, um usuário enviado sem nome, sem perfis e com um email malformado:

```json
{ "status": 400, "error": "Bad Request", "message": "Dados inválidos (perfis: não deve estar vazio; nome: não deve estar em branco; emails[].endereco: deve ser um endereço de e-mail bem formado)", "path": "/empresas/1/usuarios" }
```

### O que ficou para a próxima etapa

- **Estoque e preço da venda:** a venda não dá baixa na `quantidade` da mercadoria, cada item entra uma única vez por venda e o `valorTotal` acompanha o preço atual dos itens. Uma entidade de item de venda, com quantidade e preço congelados no momento da venda, resolveria os três pontos, mas ficaria fora do diagrama proposto.

## Navegando pela API

`GET /vendas/2` (trecho, com um item de cada lista):

```json
{
  "id": 2,
  "identificacao": "1234698749",
  "mercadorias": [
    {
      "id": 1,
      "nome": "Roda de liga leve modelo toyota etios",
      "valor": 300.0,
      "_links": {
        "self": { "href": "http://localhost:8080/mercadorias/1" },
        "up": { "href": "http://localhost:8080/empresas/1" }
      }
    }
  ],
  "servicos": [
    {
      "id": 2,
      "nome": "Alinhamento de rodas",
      "valor": 50.0,
      "_links": {
        "self": { "href": "http://localhost:8080/servicos/2" },
        "up": { "href": "http://localhost:8080/empresas/1" }
      }
    }
  ],
  "valorTotal": 380.0,
  "_links": {
    "self": { "href": "http://localhost:8080/vendas/2" },
    "collection": { "href": "http://localhost:8080/vendas" },
    "edit": { "href": "http://localhost:8080/vendas/2" },
    "cliente": { "href": "http://localhost:8080/usuarios/3" },
    "funcionario": { "href": "http://localhost:8080/usuarios/1" },
    "veiculo": { "href": "http://localhost:8080/veiculos/1" },
    "up": { "href": "http://localhost:8080/empresas/1" }
  }
}
```

O serviço 2 (alinhamento) é o mesmo da venda 1. No modelo original isso violava o `UNIQUE` da tabela de junção.

Um fluxo completo, de uma empresa nova até a venda. Os comandos usam a sintaxe do bash, com o JSON entre aspas simples, e não funcionam como estão no CMD ou no PowerShell do Windows. Lá, use o Git Bash ou o WSL, ou envie os mesmos corpos pelo Postman ou Insomnia.

```bash
# 0. Login como administrador (a empresa nova só o administrador cria)
TOKEN=$(curl -s -X POST localhost:8080/login -H 'Content-Type: application/json' \
  -d '{"nomeUsuario":"admin","senha":"123456"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
AUTH="Authorization: Bearer $TOKEN"

# 1. Empresa
curl -i -X POST localhost:8080/empresas -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"razaoSocial":"VW Serviços Ltda","nomeFantasia":"VW Car Care"}'

# 2. Cliente (com email e credencial) e vendedor, já vinculados à empresa 2 (usuários 6 e 7)
curl -i -X POST localhost:8080/empresas/2/usuarios -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"nome":"Ana","perfis":["CLIENTE"],"emails":[{"endereco":"ana@vw.com"}],
       "credenciais":[{"tipo":"usuario-senha","nomeUsuario":"ana","senha":"segredo"}]}'
curl -i -X POST localhost:8080/empresas/2/usuarios -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"nome":"Beto","perfis":["VENDEDOR"]}'

# 3. Veículo da cliente e um serviço no catálogo da empresa
curl -i -X POST localhost:8080/usuarios/6/veiculos -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"tipo":"HATCH","modelo":"polo","placa":"VWX-1234"}'
curl -i -X POST localhost:8080/empresas/2/servicos -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"nome":"Troca de pastilha","valor":80}'

# 4. Venda
curl -i -X POST localhost:8080/empresas/2/vendas -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"identificacao":"VW-001","clienteId":6,"funcionarioId":7,"veiculoId":2,"servicoIds":[4]}'

# 5. A Ana entra com a própria credencial e só enxerga o que é dela
curl -s -X POST localhost:8080/login -H 'Content-Type: application/json' -d '{"nomeUsuario":"ana","senha":"segredo"}'
```

## Valores aceitos e formatos

| Campo | Valores |
| ----- | ------- |
| `perfis` (usuário) | `ADMINISTRADOR`, `GERENTE`, `VENDEDOR`, `CLIENTE`, `FORNECEDOR` (um ou mais) |
| `tipo` (documento) | `CPF`, `CNPJ`, `RG`, `CNH`, `PASSAPORTE` |
| `tipo` (veículo) | `HATCH`, `SEDA`, `SUV`, `PICKUP`, `SW` |
| `tipo` (credencial) | `usuario-senha` (com `nomeUsuario` e `senha`) ou `codigo-barra` (com `codigo`, numérico) |

Um valor fora da lista responde `400`.

As datas enviadas (`dataEmissao` do documento, `validade` e `fabricacao` da mercadoria) usam o formato `aaaa-mm-dd`, como `"2020-01-15"`, ou o ISO com hora, como `"2020-01-15T10:00:00"`. Formatos como `15/01/2020` respondem `400`. Nas respostas, todas as datas aparecem no ISO com fuso UTC (`"2020-01-15T00:00:00.000+00:00"`).

As datas de registro (`cadastro` da empresa, da mercadoria e da venda e `criacao` da credencial) são preenchidas pelo sistema. Quando enviadas no corpo, são ignoradas.

## Referência das rotas

O `PUT` é parcial em todos os recursos: campos ausentes ou em branco mantêm o valor atual. No usuário e na empresa, o `PUT` altera só os dados do próprio recurso; telefones, documentos e os demais itens embutidos têm rotas próprias.

Todas as rotas exigem o token, exceto `POST /login`, e respondem `401` sem ele. Quais perfis usam cada rota está em [Perfis e autorizações](#perfis-e-autorizações); quando o perfil não tem permissão, a resposta é `403`. A coluna de erros abaixo lista só os de negócio.

| Método | Rota | Sucesso | Erros |
| ------ | ---- | ------- | ----- |
| `POST` | `/login` | 200 | 401 |
| `GET` | `/empresas`, `/usuarios`, `/veiculos`, `/mercadorias`, `/servicos`, `/vendas`, `/telefones`, `/enderecos`, `/documentos`, `/emails`, `/credenciais` | 200 | |
| `GET` | `/{recurso}/{id}` | 200 | 404 |
| `PUT` | `/{recurso}/{id}` | 200 | 400, 404, 409 |
| `DELETE` | `/{recurso}/{id}` | 204 | 404, 409 (consta em vendas) |
| `POST` | `/empresas` | 201 | 400 |
| `POST` | `/empresas/{id}/usuarios`, `/mercadorias`, `/servicos` | 201 | 400, 404 |
| `POST` | `/empresas/{id}/vendas` | 201 | 400 (regras da venda), 404, 409 (identificação repetida) |
| `POST` | `/usuarios/{id}/veiculos`, `/documentos`, `/emails`, `/credenciais`, `/telefones` | 201 | 400, 404, 409 (documento ou nome de usuário repetido) |
| `POST` | `/empresas/{id}/telefones` | 201 | 400, 404 |
| `POST` | `/usuarios/{id}/endereco`, `/empresas/{id}/endereco` | 201 | 400, 404, 409 (já tem endereço) |
| `PUT` | `/empresas/{id}/usuarios/{usuarioId}` | 204 | 404, 409 (usuário de outra empresa) |
| `DELETE` | `/empresas/{id}/usuarios/{usuarioId}` | 204 | 404 |
| `PUT` | `/usuarios/{id}/mercadorias/{mercadoriaId}` | 204 | 400 (não é fornecedor), 404, 409 (outro fornecedor) |
| `DELETE` | `/usuarios/{id}/mercadorias/{mercadoriaId}` | 204 | 404 |
| `GET` | `/empresas/{id}/usuarios`, `/mercadorias`, `/servicos`, `/vendas`; `/usuarios/{id}/veiculos`, `/vendas`, `/mercadorias`; `/veiculos/{id}/vendas` | 200 | 404 |

Os vínculos (`PUT` e `DELETE` em `/empresas/{id}/usuarios/{usuarioId}` e `/usuarios/{id}/mercadorias/{mercadoriaId}`) são idempotentes: repetir o `PUT` não muda nada.

O cadastro (`POST`) responde com o recurso criado, seus links e o cabeçalho `Location`. Enviar `id` no corpo de um cadastro responde `409`, porque o id é gerado pelo sistema.
