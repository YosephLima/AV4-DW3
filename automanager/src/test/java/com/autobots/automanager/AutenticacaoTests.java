package com.autobots.automanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.autobots.automanager.entidades.CredencialUsuarioSenha;
import com.autobots.automanager.repositorios.RepositorioCredencialUsuarioSenha;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutenticacaoTests {
	@Autowired
	private MockMvc mvc;
	@Autowired
	private RepositorioCredencialUsuarioSenha repositorioCredencial;
	@Value("${jwt.secret}")
	private String segredo;

	private ResultActions login(String nomeUsuario, String senha) throws Exception {
		return mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nomeUsuario\":\"" + nomeUsuario + "\",\"senha\":\"" + senha + "\"}"));
	}

	private String token(String nomeUsuario) throws Exception {
		return login(nomeUsuario, "123456").andExpect(status().isOk()).andReturn().getResponse()
				.getHeader(HttpHeaders.AUTHORIZATION);
	}

	private String jwtAssinado(String nomeUsuario, Date expiracao, String chave) {
		return "Bearer " + Jwts.builder().setSubject(nomeUsuario).setExpiration(expiracao)
				.signWith(Keys.hmacShaKeyFor(chave.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512).compact();
	}

	// ------------------------------------------------------------ login

	@Test
	void loginDevolveTokenNoCabecalhoENoCorpo() throws Exception {
		login("admin", "123456").andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.AUTHORIZATION, startsWith("Bearer ")))
				.andExpect(jsonPath("$.tipo").value("Bearer"))
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.perfis[0]").value("ADMINISTRADOR"))
				.andExpect(jsonPath("$._links.usuario.href").value("http://localhost/usuarios/4"));
	}

	@Test
	void loginRegistraUltimoAcesso() throws Exception {
		Date antes = new Date();
		token("gerente");
		CredencialUsuarioSenha credencial = repositorioCredencial.findByNomeUsuario("gerente").orElseThrow();
		assertThat(credencial.getUltimoAcesso()).isAfterOrEqualTo(antes);
	}

	@Test
	void loginRecusaSenhaErrada() throws Exception {
		login("admin", "errada").andExpect(status().isUnauthorized());
	}

	@Test
	void loginRecusaUsuarioInexistente() throws Exception {
		login("ninguem", "123456").andExpect(status().isUnauthorized());
	}

	@Test
	void loginRecusaCorpoInvalido() throws Exception {
		mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("nao e json"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void fornecedorNaoTemPerfilDeAcesso() throws Exception {
		login("dompedrofornecedor", "123456").andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------ token

	@Test
	void semTokenRecebe401() throws Exception {
		mvc.perform(get("/usuarios")).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenMalformadoRecebe401() throws Exception {
		mvc.perform(get("/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void cabecalhoSemBearerRecebe401() throws Exception {
		String token = token("admin").substring("Bearer ".length());
		mvc.perform(get("/usuarios").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenAssinadoComOutroSegredoRecebe401() throws Exception {
		String falso = jwtAssinado("admin", new Date(System.currentTimeMillis() + 60000),
				"um-segredo-qualquer-que-nao-e-o-da-aplicacao-com-mais-de-sessenta-e-quatro-bytes");
		mvc.perform(get("/usuarios").header(HttpHeaders.AUTHORIZATION, falso)).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenExpiradoRecebe401() throws Exception {
		String expirado = jwtAssinado("admin", new Date(System.currentTimeMillis() - 1000), segredo);
		mvc.perform(get("/usuarios").header(HttpHeaders.AUTHORIZATION, expirado)).andExpect(status().isUnauthorized());
	}

	@Test
	void credencialInativadaDerrubaOTokenJaEmitido() throws Exception {
		String cliente = token("dompedrocliente");
		mvc.perform(get("/usuarios/3").header(HttpHeaders.AUTHORIZATION, cliente)).andExpect(status().isOk());

		mvc.perform(put("/credenciais/3").header(HttpHeaders.AUTHORIZATION, token("admin"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"usuario-senha\",\"inativo\":true}"))
				.andExpect(status().isOk());

		mvc.perform(get("/usuarios/3").header(HttpHeaders.AUTHORIZATION, cliente)).andExpect(status().isUnauthorized());
		login("dompedrocliente", "123456").andExpect(status().isUnauthorized());
	}

	@Test
	void perfilAlteradoValeNaHora() throws Exception {
		String vendedor = token("dompedrovendedor");
		mvc.perform(get("/servicos").header(HttpHeaders.AUTHORIZATION, vendedor)).andExpect(status().isOk());

		mvc.perform(put("/usuarios/1").header(HttpHeaders.AUTHORIZATION, token("admin"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"perfis\":[\"CLIENTE\"]}"))
				.andExpect(status().isOk());

		mvc.perform(get("/servicos").header(HttpHeaders.AUTHORIZATION, vendedor)).andExpect(status().isForbidden());
	}

	// ------------------------------------------------------------ senha

	@Test
	void senhaEGravadaComBcryptENuncaDevolvida() throws Exception {
		mvc.perform(post("/usuarios/3/credenciais").header(HttpHeaders.AUTHORIZATION, token("admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"usuario-senha\",\"nomeUsuario\":\"novocliente\",\"senha\":\"segredo1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.senha").doesNotExist());

		assertThat(repositorioCredencial.findByNomeUsuario("novocliente").orElseThrow().getSenha()).startsWith("$2a$");
		login("novocliente", "segredo1").andExpect(status().isOk());
	}

	@Test
	void novaSenhaNoPutTambemEGravadaComBcrypt() throws Exception {
		mvc.perform(put("/credenciais/3").header(HttpHeaders.AUTHORIZATION, token("admin"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"usuario-senha\",\"senha\":\"trocada\"}"))
				.andExpect(status().isOk());

		login("dompedrocliente", "123456").andExpect(status().isUnauthorized());
		login("dompedrocliente", "trocada").andExpect(status().isOk());
	}

	@Test
	void senhaDoUsuarioCadastradoComCredencialEmbutidaTambemEGravadaComBcrypt() throws Exception {
		mvc.perform(post("/empresas/1/usuarios").header(HttpHeaders.AUTHORIZATION, token("dompedrovendedor"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Ana\",\"perfis\":[\"CLIENTE\"],\"credenciais\":[{\"tipo\":\"usuario-senha\",\"nomeUsuario\":\"ana\",\"senha\":\"segredo\"}]}"))
				.andExpect(status().isCreated());

		assertThat(repositorioCredencial.findByNomeUsuario("ana").orElseThrow().getSenha()).startsWith("$2a$");
		login("ana", "segredo").andExpect(status().isOk());
	}

	// ------------------------------------------------------------ listagens filtradas

	@Test
	void clienteListaSoOProprioCadastro() throws Exception {
		mvc.perform(get("/usuarios").header(HttpHeaders.AUTHORIZATION, token("dompedrocliente")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$._embedded.usuarios", hasSize(1)))
				.andExpect(jsonPath("$._embedded.usuarios[0].id").value(3));
	}

	@Test
	void vendedorListaSoClientesEOProprioCadastro() throws Exception {
		mvc.perform(get("/usuarios").header(HttpHeaders.AUTHORIZATION, token("dompedrovendedor")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$._embedded.usuarios", hasSize(2)));
	}

	@Test
	void vendedorListaSoAsVendasQueFez() throws Exception {
		mvc.perform(post("/empresas/1/vendas").header(HttpHeaders.AUTHORIZATION, token("gerente"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"identificacao\":\"VENDA-DO-GERENTE\",\"clienteId\":3,\"funcionarioId\":5,\"servicoIds\":[1]}"))
				.andExpect(status().isCreated());

		mvc.perform(get("/vendas").header(HttpHeaders.AUTHORIZATION, token("dompedrovendedor")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$._embedded.vendas", hasSize(2)))
				.andExpect(content().string(not(containsString("VENDA-DO-GERENTE"))));
		// O cliente da venda enxerga a venda, mesmo feita por outro funcionário
		mvc.perform(get("/vendas").header(HttpHeaders.AUTHORIZATION, token("dompedrocliente")))
				.andExpect(jsonPath("$._embedded.vendas", hasSize(3)));
	}

	@Test
	void vendedorSemFuncionarioNaVendaRecebe400() throws Exception {
		mvc.perform(post("/empresas/1/vendas").header(HttpHeaders.AUTHORIZATION, token("dompedrovendedor"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"identificacao\":\"SEM-FUNC\",\"clienteId\":3,\"servicoIds\":[1]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void funcionarioDaVendaPrecisaDePerfilDeFuncionario() throws Exception {
		mvc.perform(post("/empresas/1/vendas").header(HttpHeaders.AUTHORIZATION, token("gerente"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"identificacao\":\"FUNC-CLIENTE\",\"clienteId\":3,\"funcionarioId\":3,\"servicoIds\":[1]}"))
				.andExpect(status().isBadRequest());
	}
}
