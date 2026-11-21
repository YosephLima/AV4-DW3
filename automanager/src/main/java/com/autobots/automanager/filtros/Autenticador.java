package com.autobots.automanager.filtros;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.autobots.automanager.adaptadores.UserDetailsImpl;
import com.autobots.automanager.adaptadores.UserDetailsServiceImpl;
import com.autobots.automanager.controles.UsuarioControle;
import com.autobots.automanager.jwt.ProvedorJwt;
import com.autobots.automanager.modelos.DadosLogin;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Login: atende o POST /login (rota padrão do UsernamePasswordAuthenticationFilter), confere
 * nome de usuário e senha e devolve o JWT no cabeçalho Authorization, como no modelo do
 * professor, e também no corpo, com o link para o usuário autenticado.
 */
public class Autenticador extends UsernamePasswordAuthenticationFilter {
	private AuthenticationManager gerenciadorAutenticacao;
	private ProvedorJwt provedorJwt;
	private UserDetailsServiceImpl servico;
	private ObjectMapper mapeador;

	public Autenticador(AuthenticationManager gerenciadorAutenticacao, ProvedorJwt provedorJwt,
			UserDetailsServiceImpl servico, ObjectMapper mapeador) {
		this.gerenciadorAutenticacao = gerenciadorAutenticacao;
		this.provedorJwt = provedorJwt;
		this.servico = servico;
		this.mapeador = mapeador;
	}

	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
			throws AuthenticationException {
		DadosLogin dados;
		try {
			dados = mapeador.readValue(request.getInputStream(), DadosLogin.class);
		} catch (IOException e) {
			dados = new DadosLogin();
		}
		String nomeUsuario = dados.getNomeUsuario() == null ? "" : dados.getNomeUsuario();
		String senha = dados.getSenha() == null ? "" : dados.getSenha();
		return gerenciadorAutenticacao
				.authenticate(new UsernamePasswordAuthenticationToken(nomeUsuario, senha, new ArrayList<>()));
	}

	@Override
	protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain, Authentication autenticacao) throws IOException, ServletException {
		UserDetailsImpl usuario = (UserDetailsImpl) autenticacao.getPrincipal();
		String jwt = provedorJwt.proverJwt(usuario.getUsername());
		servico.registrarAcesso(usuario.getUsername());

		Map<String, Object> corpo = new LinkedHashMap<>();
		corpo.put("tipo", "Bearer");
		corpo.put("token", jwt);
		corpo.put("expiracao", provedorJwt.obterExpiracao(jwt));
		corpo.put("perfis", usuario.getPerfis());
		String href = linkTo(methodOn(UsuarioControle.class).obterUsuario(usuario.getId())).toUri().toString();
		corpo.put("_links", Map.of("usuario", Map.of("href", href)));

		response.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
		response.setStatus(HttpStatus.OK.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		mapeador.writeValue(response.getWriter(), corpo);
	}

	// Mesma mensagem para senha errada, usuário inexistente, credencial inativa ou usuário sem
	// perfil de acesso, para não revelar quais nomes de usuário existem
	@Override
	protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException falha) throws IOException, ServletException {
		SecurityContextHolder.clearContext();
		response.sendError(HttpStatus.UNAUTHORIZED.value(),
				"Login recusado: nome de usuário ou senha inválidos, credencial inativa ou usuário sem perfil de acesso");
	}
}
