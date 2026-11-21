package com.autobots.automanager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tabela 1 da ATVIV aplicada às rotas: cada caso faz login com um perfil do seed, chama uma rota
 * e confere o status. Cada caso roda numa transação desfeita no fim, então todos partem do seed:
 *
 * <pre>
 * usuários:   1 vendedor (dompedrovendedor), 2 fornecedor, 3 cliente (dompedrocliente),
 *             4 administrador (admin), 5 gerente (gerente)
 * credenciais e emails seguem a mesma ordem (credencial 3 e email 3 são do cliente)
 * vendas 1 e 2: cliente 3, funcionário 1, veículo 1 (do cliente)
 * catálogo da empresa 1: mercadoria 1; serviços 1, 2 e 3
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TabelaPerfisTests {
	private static final String ADMIN = "admin";
	private static final String GERENTE = "gerente";
	private static final String VENDEDOR = "dompedrovendedor";
	private static final String CLIENTE = "dompedrocliente";

	@Autowired
	private MockMvc mvc;

	private final Map<String, String> tokens = new HashMap<>();

	private String token(String nomeUsuario) throws Exception {
		if (!tokens.containsKey(nomeUsuario)) {
			String cabecalho = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"nomeUsuario\":\"" + nomeUsuario + "\",\"senha\":\"123456\"}"))
					.andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.AUTHORIZATION);
			tokens.put(nomeUsuario, cabecalho);
		}
		return tokens.get(nomeUsuario);
	}

	private static Arguments caso(String perfil, String metodo, String rota, String corpo, int esperado) {
		return Arguments.of(perfil, metodo, rota, corpo, esperado);
	}

	private static String usuario(String perfis) {
		return "{\"nome\":\"Novo\",\"perfis\":[" + perfis + "]}";
	}

	private static final String VENDA_DO_VENDEDOR = "{\"identificacao\":\"T-1\",\"clienteId\":3,\"funcionarioId\":1,\"servicoIds\":[1]}";
	private static final String VENDA_DO_GERENTE = "{\"identificacao\":\"T-2\",\"clienteId\":3,\"funcionarioId\":5,\"servicoIds\":[1]}";
	private static final String SERVICO = "{\"nome\":\"Lavagem\",\"valor\":40}";
	private static final String TELEFONE = "{\"ddd\":\"12\",\"numero\":\"99999\"}";
	private static final String NOME_SOCIAL = "{\"nomeSocial\":\"Outro nome\"}";

	static Stream<Arguments> casos() {
		return Stream.of(
				// Administrador: tudo, inclusive administradores, fornecedores e a empresa
				caso(ADMIN, "GET", "/usuarios/4", null, 200),
				caso(ADMIN, "GET", "/usuarios/2", null, 200),
				caso(ADMIN, "POST", "/empresas/1/usuarios", usuario("\"ADMINISTRADOR\""), 201),
				caso(ADMIN, "PUT", "/usuarios/5", "{\"perfis\":[\"ADMINISTRADOR\"]}", 200),
				caso(ADMIN, "DELETE", "/usuarios/5", null, 204),
				caso(ADMIN, "POST", "/empresas", "{\"razaoSocial\":\"Nova\"}", 201),
				caso(ADMIN, "PUT", "/empresas/1", "{\"nomeFantasia\":\"Outra\"}", 200),
				caso(ADMIN, "GET", "/credenciais", null, 200),
				caso(ADMIN, "GET", "/usuarios/99", null, 404),

				// Gerente: usuários gerente, vendedor e cliente; serviços, mercadorias e vendas
				caso(GERENTE, "GET", "/usuarios/5", null, 200),
				caso(GERENTE, "GET", "/usuarios/1", null, 200),
				caso(GERENTE, "GET", "/usuarios/3", null, 200),
				caso(GERENTE, "GET", "/usuarios/4", null, 403),
				caso(GERENTE, "GET", "/usuarios/2", null, 403),
				caso(GERENTE, "POST", "/empresas/1/usuarios", usuario("\"GERENTE\""), 201),
				caso(GERENTE, "POST", "/empresas/1/usuarios", usuario("\"VENDEDOR\",\"CLIENTE\""), 201),
				caso(GERENTE, "POST", "/empresas/1/usuarios", usuario("\"ADMINISTRADOR\""), 403),
				caso(GERENTE, "POST", "/empresas/1/usuarios", usuario("\"FORNECEDOR\""), 403),
				caso(GERENTE, "PUT", "/usuarios/1", NOME_SOCIAL, 200),
				caso(GERENTE, "PUT", "/usuarios/1", "{\"perfis\":[\"ADMINISTRADOR\"]}", 403),
				caso(GERENTE, "PUT", "/usuarios/4", NOME_SOCIAL, 403),
				caso(GERENTE, "DELETE", "/usuarios/4", null, 403),
				caso(GERENTE, "POST", "/usuarios/3/telefones", TELEFONE, 201),
				caso(GERENTE, "POST", "/usuarios/4/telefones", TELEFONE, 403),
				caso(GERENTE, "GET", "/credenciais/3", null, 200),
				caso(GERENTE, "GET", "/credenciais/4", null, 403),
				caso(GERENTE, "GET", "/credenciais", null, 403),
				caso(GERENTE, "POST", "/empresas/1/servicos", SERVICO, 201),
				caso(GERENTE, "PUT", "/servicos/1", "{\"valor\":60}", 200),
				caso(GERENTE, "PUT", "/mercadorias/1", "{\"valor\":310}", 200),
				caso(GERENTE, "POST", "/empresas/1/vendas", VENDA_DO_GERENTE, 201),
				caso(GERENTE, "PUT", "/vendas/1", "{\"servicoIds\":[1]}", 200),
				caso(GERENTE, "DELETE", "/vendas/1", null, 204),
				caso(GERENTE, "GET", "/vendas/99", null, 404),
				caso(GERENTE, "GET", "/empresas/1", null, 200),
				caso(GERENTE, "PUT", "/empresas/1", "{\"nomeFantasia\":\"Outra\"}", 403),
				caso(GERENTE, "POST", "/empresas", "{\"razaoSocial\":\"Nova\"}", 403),
				caso(GERENTE, "POST", "/empresas/1/telefones", TELEFONE, 403),

				// Vendedor: usuários cliente; lê serviços e mercadorias; cria e lê as próprias vendas
				caso(VENDEDOR, "GET", "/usuarios/1", null, 200),
				caso(VENDEDOR, "GET", "/usuarios/3", null, 200),
				caso(VENDEDOR, "GET", "/usuarios/5", null, 403),
				caso(VENDEDOR, "GET", "/usuarios/4", null, 403),
				caso(VENDEDOR, "POST", "/empresas/1/usuarios", usuario("\"CLIENTE\""), 201),
				caso(VENDEDOR, "POST", "/empresas/1/usuarios", usuario("\"VENDEDOR\""), 403),
				caso(VENDEDOR, "POST", "/empresas/1/usuarios", usuario("\"CLIENTE\",\"FORNECEDOR\""), 403),
				caso(VENDEDOR, "PUT", "/usuarios/3", NOME_SOCIAL, 200),
				caso(VENDEDOR, "PUT", "/usuarios/3", "{\"perfis\":[\"VENDEDOR\"]}", 403),
				caso(VENDEDOR, "PUT", "/usuarios/1", NOME_SOCIAL, 403),
				caso(VENDEDOR, "DELETE", "/usuarios/5", null, 403),
				caso(VENDEDOR, "POST", "/usuarios/3/credenciais",
						"{\"tipo\":\"usuario-senha\",\"nomeUsuario\":\"cliente2\",\"senha\":\"abc\"}", 201),
				caso(VENDEDOR, "POST", "/usuarios/3/veiculos", "{\"tipo\":\"SUV\",\"modelo\":\"hilux\",\"placa\":\"XYZ-9999\"}", 201),
				caso(VENDEDOR, "GET", "/veiculos/1", null, 200),
				caso(VENDEDOR, "GET", "/servicos", null, 200),
				caso(VENDEDOR, "GET", "/mercadorias/1", null, 200),
				caso(VENDEDOR, "POST", "/empresas/1/servicos", SERVICO, 403),
				caso(VENDEDOR, "PUT", "/mercadorias/1", "{\"valor\":1}", 403),
				caso(VENDEDOR, "DELETE", "/servicos/1", null, 403),
				caso(VENDEDOR, "POST", "/empresas/1/vendas", VENDA_DO_VENDEDOR, 201),
				caso(VENDEDOR, "POST", "/empresas/1/vendas", VENDA_DO_GERENTE, 403),
				caso(VENDEDOR, "GET", "/vendas/1", null, 200),
				caso(VENDEDOR, "PUT", "/vendas/1", "{\"servicoIds\":[1]}", 403),
				caso(VENDEDOR, "DELETE", "/vendas/1", null, 403),
				caso(VENDEDOR, "GET", "/empresas/1", null, 200),
				caso(VENDEDOR, "GET", "/credenciais", null, 403),

				// Cliente: lê o próprio cadastro e as vendas em que foi o cliente
				caso(CLIENTE, "GET", "/usuarios/3", null, 200),
				caso(CLIENTE, "GET", "/usuarios/3/veiculos", null, 200),
				caso(CLIENTE, "GET", "/usuarios/3/vendas", null, 200),
				caso(CLIENTE, "GET", "/veiculos/1", null, 200),
				caso(CLIENTE, "GET", "/credenciais/3", null, 200),
				caso(CLIENTE, "GET", "/emails/3", null, 200),
				caso(CLIENTE, "GET", "/vendas/1", null, 200),
				caso(CLIENTE, "GET", "/usuarios/1", null, 403),
				caso(CLIENTE, "GET", "/credenciais/1", null, 403),
				caso(CLIENTE, "GET", "/emails/1", null, 403),
				caso(CLIENTE, "PUT", "/usuarios/3", NOME_SOCIAL, 403),
				caso(CLIENTE, "DELETE", "/usuarios/3", null, 403),
				caso(CLIENTE, "POST", "/usuarios/3/telefones", TELEFONE, 403),
				caso(CLIENTE, "GET", "/servicos", null, 403),
				caso(CLIENTE, "GET", "/mercadorias", null, 403),
				caso(CLIENTE, "GET", "/empresas/1", null, 403),
				caso(CLIENTE, "POST", "/empresas/1/vendas", VENDA_DO_VENDEDOR, 403),
				caso(CLIENTE, "PUT", "/vendas/1", "{\"servicoIds\":[1]}", 403),
				// Id inexistente: quem não enxergaria o registro recebe 403, sem saber se ele existe
				caso(CLIENTE, "GET", "/vendas/99", null, 403),
				caso(CLIENTE, "GET", "/usuarios/99", null, 403));
	}

	@ParameterizedTest(name = "{0} {1} {2} -> {4}")
	@MethodSource("casos")
	void aplicaTabela1(String perfil, String metodo, String rota, String corpo, int esperado) throws Exception {
		MockHttpServletRequestBuilder requisicao = request(HttpMethod.valueOf(metodo), rota)
				.header(HttpHeaders.AUTHORIZATION, token(perfil));
		if (corpo != null) {
			requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
		}
		mvc.perform(requisicao).andExpect(status().is(esperado));
	}
}
