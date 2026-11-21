#!/usr/bin/env bash
# Roteiro de verificação da API: cada linha faz uma requisição e confere o status HTTP esperado.
# Os ids partem do seed, então rode com a aplicação recém-iniciada:
#   ./testes/roteiro.sh                          (API em http://localhost:8080)
#   B=http://localhost:8081 ./testes/roteiro.sh  (outra porta)
#
# A primeira parte repete o CRUD da ATVIII com o token do administrador. A segunda percorre a
# Tabela 1 da ATVIV com o token de cada perfil.

B=${B:-http://localhost:8080}
falhas=0
total=0

# Faz login e devolve só o token
login() {
	curl -s -X POST "$B/login" -H 'Content-Type: application/json' \
		-d "{\"nomeUsuario\":\"$1\",\"senha\":\"$2\"}" | grep -o '"token":"[^"]*"' | cut -d'"' -f4
}

# r MÉTODO ROTA CORPO STATUS_ESPERADO [TOKEN]
# Sem TOKEN usa o do administrador; com "-" envia a requisição sem o cabeçalho Authorization
r() {
	local metodo=$1 rota=$2 corpo=$3 esperado=$4 token=${5:-$ADMIN} saida status mensagem
	local args=(-s -i -X "$metodo")
	[ "$token" != "-" ] && args+=(-H "Authorization: Bearer $token")
	[ -n "$corpo" ] && args+=(-H 'Content-Type: application/json' -d "$corpo")
	saida=$(curl "${args[@]}" "$B$rota")
	status=$(echo "$saida" | grep -m1 '^HTTP' | tr -d '\r' | cut -d' ' -f2)
	mensagem=$(echo "$saida" | tail -1 | grep -o '"message":"[^"]*"')
	total=$((total + 1))
	if [ "$status" = "$esperado" ]; then
		echo "ok     $metodo $rota -> $status $mensagem"
	else
		falhas=$((falhas + 1))
		echo "FALHOU $metodo $rota -> ${status:-sem resposta} (esperado $esperado) $mensagem"
	fi
}

# sem ROTA TEXTO TOKEN: confere que a listagem não traz o texto (filtro por perfil)
sem() {
	local rota=$1 texto=$2 token=$3
	total=$((total + 1))
	if curl -s -H "Authorization: Bearer $token" "$B$rota" | grep -q "$texto"; then
		falhas=$((falhas + 1))
		echo "FALHOU GET $rota trouxe \"$texto\", que este perfil não deveria ver"
	else
		echo "ok     GET $rota não traz \"$texto\""
	fi
}

ADMIN=$(login admin 123456)
if [ -z "$ADMIN" ]; then
	echo "Não foi possível fazer login como admin em $B. A API está no ar, recém-iniciada?"
	exit 1
fi

echo "=== Parte 1: CRUD da ATVIII, como administrador"

# Consultas sobre o seed
for rota in /empresas /usuarios /veiculos /mercadorias /servicos /vendas /telefones /enderecos /documentos /emails /credenciais \
	/empresas/1/usuarios /empresas/1/mercadorias /empresas/1/servicos /empresas/1/vendas \
	/usuarios/3/vendas /usuarios/3/veiculos /usuarios/2/mercadorias /veiculos/1/vendas /vendas/1 /usuarios/1 /usuarios/4 /empresas/1; do
	r GET $rota "" 200
done
r GET /vendas/99 "" 404

# Empresa
r POST /empresas '{"razaoSocial":"VW Serviços Ltda","nomeFantasia":"VW Car Care"}' 201
r POST /empresas '{"nomeFantasia":"sem razao"}' 400
r POST /empresas '{"id":1,"razaoSocial":"x"}' 409
r PUT /empresas/2 '{"nomeFantasia":"VW Car Care Wolfsburg"}' 200
r POST /empresas/2/telefones '{"ddd":"11","numero":"40028922"}' 201
r POST /empresas/2/endereco '{"estado":"SP","cidade":"São Bernardo","bairro":"Centro","rua":"Via Anchieta","numero":"1","codigoPostal":"09823-000"}' 201
r POST /empresas/2/endereco '{"estado":"SP","cidade":"x","bairro":"x","rua":"x","numero":"1","codigoPostal":"1"}' 409

# Usuários, itens embutidos e credenciais. O seed tem 5 usuários, então Ana é a 6 e Beto o 7
r POST /empresas/2/usuarios '{"nome":"Ana Cliente","perfis":["CLIENTE"],"emails":[{"endereco":"ana@vw.com"}],"documentos":[{"tipo":"CPF","numero":"99988877766","dataEmissao":"2020-01-01"}],"credenciais":[{"tipo":"usuario-senha","nomeUsuario":"ana","senha":"segredo"}]}' 201
r POST /empresas/2/usuarios '{"nome":"Beto Vendedor","perfis":["VENDEDOR"]}' 201
r POST /empresas/2/usuarios '{"nome":"Sem perfil"}' 400
r POST /empresas/2/usuarios '{"nome":"Perfil da ATVIII","perfis":["FUNCIONARIO"]}' 400
r POST /empresas/2/usuarios '{"nome":"Cred sem tipo","perfis":["CLIENTE"],"credenciais":[{"nomeUsuario":"x","senha":"y"}]}' 400
r POST /usuarios/6/telefones '{"ddd":"12","numero":"99999"}' 201
r POST /usuarios/6/emails '{"endereco":"ana2@vw.com"}' 201
r POST /usuarios/6/documentos '{"tipo":"RG","numero":"1234","dataEmissao":"2010-05-05"}' 201
r POST /usuarios/6/documentos '{"tipo":"RG","numero":"1234","dataEmissao":"2010-05-05"}' 409
r POST /usuarios/6/endereco '{"estado":"SP","cidade":"SJC","bairro":"Centro","rua":"Rua A","numero":"10","codigoPostal":"12200-000"}' 201
r POST /usuarios/6/credenciais '{"tipo":"codigo-barra","codigo":123456789}' 201
r POST /usuarios/6/credenciais '{"tipo":"usuario-senha","nomeUsuario":"ana","senha":"outra"}' 409
r PUT /usuarios/6 '{"nomeSocial":"Aninha","perfis":["CLIENTE","FORNECEDOR"]}' 200
r PUT /emails/4 '{"endereco":"invalido"}' 400

# Vínculo de usuário com empresa
r PUT /empresas/2/usuarios/6 "" 204
r PUT /empresas/2/usuarios/1 "" 409
r DELETE /empresas/2/usuarios/3 "" 404

# Veículo, catálogo e fornecedor
r POST /usuarios/6/veiculos '{"tipo":"HATCH","modelo":"polo","placa":"VWX-1234"}' 201
r POST /usuarios/6/veiculos '{"tipo":"CARRO","modelo":"x","placa":"y"}' 400
r PUT /veiculos/2 '{"modelo":"polo gts"}' 200
r POST /empresas/2/mercadorias '{"nome":"Pastilha de freio","quantidade":10,"valor":120.5,"validade":"2030-01-01","fabricacao":"2025-01-01"}' 201
r POST /empresas/2/servicos '{"nome":"Troca de pastilha","valor":80}' 201
r PUT /servicos/4 '{"valor":90}' 200
r PUT /usuarios/2/mercadorias/2 "" 204
r PUT /usuarios/3/mercadorias/2 "" 400
r PUT /mercadorias/2 '{"valor":130.0}' 200

# Regras da venda
r POST /empresas/2/vendas '{"identificacao":"VW-001","clienteId":6,"funcionarioId":7,"veiculoId":2,"mercadoriaIds":[2],"servicoIds":[4]}' 201
r POST /empresas/2/vendas '{"identificacao":"VW-001","clienteId":6,"funcionarioId":7,"servicoIds":[4]}' 409
r POST /empresas/2/vendas '{"identificacao":"VW-002","clienteId":7,"funcionarioId":7,"servicoIds":[4]}' 400
r POST /empresas/2/vendas '{"identificacao":"VW-003","clienteId":3,"funcionarioId":7,"servicoIds":[4]}' 400
r POST /empresas/2/vendas '{"identificacao":"VW-004","clienteId":6,"funcionarioId":7,"veiculoId":1,"servicoIds":[4]}' 400
r POST /empresas/2/vendas '{"identificacao":"VW-005","clienteId":6,"funcionarioId":7,"servicoIds":[1]}' 400
r POST /empresas/2/vendas '{"identificacao":"VW-006","clienteId":6,"funcionarioId":7}' 400
# O 409 acima consumiu um id, por isso esta venda é a 5
r POST /empresas/2/vendas '{"identificacao":"VW-007","clienteId":6,"funcionarioId":7,"servicoIds":[4]}' 201
r PUT /vendas/5 '{"mercadoriaIds":[2]}' 200
r PUT /vendas/5 '{"servicoIds":[],"mercadoriaIds":[]}' 400

# Exclusões bloqueadas pelo histórico de vendas
r DELETE /usuarios/6 "" 409
r DELETE /veiculos/2 "" 409
r DELETE /servicos/4 "" 409
r DELETE /mercadorias/2 "" 409
r DELETE /empresas/2 "" 409

# Sem as vendas, tudo pode ser excluído
r DELETE /vendas/3 "" 204
r DELETE /vendas/5 "" 204
r DELETE /veiculos/2 "" 204
r DELETE /mercadorias/2 "" 204
r DELETE /servicos/4 "" 204
r DELETE /credenciais/7 "" 204
r DELETE /emails/4 "" 204
r DELETE /documentos/4 "" 204
r DELETE /telefones/4 "" 204
r DELETE /enderecos/6 "" 204
r DELETE /usuarios/6 "" 204
r DELETE /empresas/2 "" 204
r GET /empresas/2 "" 404
# Excluir a empresa só desvincula o usuário, sem apagá-lo
r GET /usuarios/7 "" 200

echo
echo "=== Parte 2: autenticação e Tabela 1 da ATVIV"

# Autenticação
r POST /login '{"nomeUsuario":"admin","senha":"123456"}' 200 -
r POST /login '{"nomeUsuario":"admin","senha":"errada"}' 401 -
r POST /login '{"nomeUsuario":"ninguem","senha":"123456"}' 401 -
r POST /login '{"nomeUsuario":"dompedrofornecedor","senha":"123456"}' 401 -
r GET /usuarios "" 401 -
r GET /usuarios "" 401 token-invalido

GERENTE=$(login gerente 123456)
VENDEDOR=$(login dompedrovendedor 123456)
CLIENTE=$(login dompedrocliente 123456)

# Gerente: usuários gerente, vendedor e cliente; serviços, mercadorias e vendas
r GET /usuarios/5 "" 200 "$GERENTE"
r GET /usuarios/1 "" 200 "$GERENTE"
r GET /usuarios/4 "" 403 "$GERENTE"
r GET /usuarios/2 "" 403 "$GERENTE"
r POST /empresas/1/usuarios '{"nome":"Gil Gerente","perfis":["GERENTE"],"credenciais":[{"tipo":"usuario-senha","nomeUsuario":"gil","senha":"123456"}]}' 201 "$GERENTE"
r POST /empresas/1/usuarios '{"nome":"Promovido","perfis":["ADMINISTRADOR"]}' 403 "$GERENTE"
r PUT /usuarios/1 '{"nomeSocial":"Dom Pedro"}' 200 "$GERENTE"
r PUT /usuarios/1 '{"perfis":["ADMINISTRADOR"]}' 403 "$GERENTE"
r DELETE /usuarios/4 "" 403 "$GERENTE"
r POST /empresas/1/servicos '{"nome":"Lavagem","valor":40}' 201 "$GERENTE"
r PUT /mercadorias/1 '{"valor":310}' 200 "$GERENTE"
# Venda feita pelo gerente (5) para o cliente 3: é a venda 6
r POST /empresas/1/vendas '{"identificacao":"G-001","clienteId":3,"funcionarioId":5,"servicoIds":[1]}' 201 "$GERENTE"
r GET /empresas/1 "" 200 "$GERENTE"
r PUT /empresas/1 '{"nomeFantasia":"Outra"}' 403 "$GERENTE"
r GET /credenciais "" 403 "$GERENTE"
GIL=$(login gil 123456)
r GET /usuarios/1 "" 200 "$GIL"

# Vendedor: usuários cliente; lê serviços e mercadorias; cria e lê as próprias vendas
r GET /usuarios/1 "" 200 "$VENDEDOR"
r GET /usuarios/3 "" 200 "$VENDEDOR"
r GET /usuarios/5 "" 403 "$VENDEDOR"
# Carla, cadastrada pelo vendedor, é a usuária 9 (Gil é o 8)
r POST /empresas/1/usuarios '{"nome":"Carla Cliente","perfis":["CLIENTE"],"credenciais":[{"tipo":"usuario-senha","nomeUsuario":"carla","senha":"123456"}]}' 201 "$VENDEDOR"
r POST /empresas/1/usuarios '{"nome":"Outro Vendedor","perfis":["VENDEDOR"]}' 403 "$VENDEDOR"
r PUT /usuarios/3 '{"perfis":["VENDEDOR"]}' 403 "$VENDEDOR"
r GET /servicos "" 200 "$VENDEDOR"
r POST /empresas/1/servicos '{"nome":"Polimento","valor":1}' 403 "$VENDEDOR"
# Venda feita pelo vendedor (1) para a Carla: é a venda 7
r POST /empresas/1/vendas '{"identificacao":"V-001","clienteId":9,"funcionarioId":1,"servicoIds":[1]}' 201 "$VENDEDOR"
r POST /empresas/1/vendas '{"identificacao":"V-002","clienteId":9,"funcionarioId":5,"servicoIds":[1]}' 403 "$VENDEDOR"
r GET /vendas/1 "" 200 "$VENDEDOR"
r GET /vendas/6 "" 403 "$VENDEDOR"
sem /vendas G-001 "$VENDEDOR"
r PUT /vendas/1 '{"servicoIds":[1]}' 403 "$VENDEDOR"
r DELETE /vendas/7 "" 403 "$VENDEDOR"

# Cliente: lê o próprio cadastro e as vendas em que foi o cliente
r GET /usuarios/3 "" 200 "$CLIENTE"
r GET /usuarios/1 "" 403 "$CLIENTE"
r GET /vendas/6 "" 200 "$CLIENTE"
r GET /vendas/7 "" 403 "$CLIENTE"
sem /vendas V-001 "$CLIENTE"
r GET /servicos "" 403 "$CLIENTE"
r PUT /usuarios/3 '{"nomeSocial":"Outro"}' 403 "$CLIENTE"
# Id inexistente: o cliente recebe 403, sem descobrir se a venda existe; o administrador recebe 404
r GET /vendas/99 "" 403 "$CLIENTE"
r GET /vendas/99 "" 404

# Perfis alterados valem na hora, mesmo para um token já emitido
CARLA=$(login carla 123456)
r GET /vendas/7 "" 200 "$CARLA"
r PUT /usuarios/9 '{"perfis":["FORNECEDOR"]}' 200
r GET /vendas/7 "" 401 "$CARLA"

echo
echo "$((total - falhas)) de $total verificações passaram"
[ "$falhas" -eq 0 ]
