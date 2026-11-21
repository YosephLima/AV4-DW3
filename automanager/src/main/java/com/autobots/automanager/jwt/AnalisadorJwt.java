package com.autobots.automanager.jwt;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

class AnalisadorJwt {
	private SecretKey chave;
	private String jwt;

	public AnalisadorJwt(SecretKey chave, String jwt) {
		this.chave = chave;
		this.jwt = jwt;
	}

	// Devolve null se o token for malformado, tiver assinatura inválida ou estiver expirado
	public Claims obterReivindicacoes() {
		try {
			return Jwts.parserBuilder().setSigningKey(chave).build().parseClaimsJws(jwt).getBody();
		} catch (JwtException | IllegalArgumentException e) {
			return null;
		}
	}

	public String obterNomeUsuario(Claims reivindicacoes) {
		return reivindicacoes == null ? null : reivindicacoes.getSubject();
	}
}
