package com.autobots.automanager.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;

/**
 * Ponto de entrada do JWT, como no modelo do professor: gera o token no login, valida o token
 * de cada requisição e extrai dele o nome de usuário.
 */
@Component
public class ProvedorJwt {
	// HS512 exige uma chave de ao menos 512 bits
	private static final int TAMANHO_MINIMO_SEGREDO = 64;

	@Value("${jwt.secret}")
	private String assinatura;
	@Value("${jwt.expiration}")
	private Long duracao;

	private SecretKey chave;

	@PostConstruct
	void prepararChave() {
		byte[] bytes = assinatura.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < TAMANHO_MINIMO_SEGREDO) {
			throw new IllegalStateException("O segredo do JWT (jwt.secret / JWT_SECRET) precisa de ao menos "
					+ TAMANHO_MINIMO_SEGREDO + " bytes para o HS512, e tem " + bytes.length);
		}
		chave = Keys.hmacShaKeyFor(bytes);
	}

	public String proverJwt(String nomeUsuario) {
		return new GeradorJwt(chave, duracao).gerarJwt(nomeUsuario);
	}

	public boolean validarJwt(String jwt) {
		Claims reivindicacoes = new AnalisadorJwt(chave, jwt).obterReivindicacoes();
		return new ValidadorJwt().validar(reivindicacoes);
	}

	public String obterNomeUsuario(String jwt) {
		AnalisadorJwt analisador = new AnalisadorJwt(chave, jwt);
		return analisador.obterNomeUsuario(analisador.obterReivindicacoes());
	}

	public Date obterExpiracao(String jwt) {
		Claims reivindicacoes = new AnalisadorJwt(chave, jwt).obterReivindicacoes();
		return reivindicacoes == null ? null : reivindicacoes.getExpiration();
	}
}
