package com.autobots.automanager.jwt;

import java.util.Date;

import io.jsonwebtoken.Claims;

class ValidadorJwt {
	public boolean validar(Claims reivindicacoes) {
		if (reivindicacoes == null) {
			return false;
		}
		String nomeUsuario = reivindicacoes.getSubject();
		Date expiracao = reivindicacoes.getExpiration();
		return nomeUsuario != null && expiracao != null && new Date().before(expiracao);
	}
}
