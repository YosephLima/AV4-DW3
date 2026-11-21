package com.autobots.automanager.filtros;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import com.autobots.automanager.jwt.ProvedorJwt;

/**
 * Lê o cabeçalho "Authorization: Bearer <token>" de cada requisição e, se o token for válido,
 * coloca o usuário no contexto de segurança. Sem token válido a requisição segue anônima, e a
 * configuração de segurança responde 401 nas rotas protegidas.
 */
public class Autorizador extends BasicAuthenticationFilter {
	private ProvedorJwt provedorJwt;
	private UserDetailsService servico;

	public Autorizador(AuthenticationManager gerenciadorAutenticacao, ProvedorJwt provedorJwt,
			UserDetailsService servico) {
		super(gerenciadorAutenticacao);
		this.provedorJwt = provedorJwt;
		this.servico = servico;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		String cabecalho = request.getHeader("Authorization");
		ValidadorCabecalho validador = new ValidadorCabecalho(cabecalho);
		if (validador.validar()) {
			String jwt = new AnalisadorCabecalho(cabecalho).obterJwt();
			UsernamePasswordAuthenticationToken autenticacao = new AutenticadorJwt(jwt, provedorJwt, servico)
					.obterAutenticacao();
			if (autenticacao != null) {
				SecurityContextHolder.getContext().setAuthentication(autenticacao);
			}
		}
		chain.doFilter(request, response);
	}
}
