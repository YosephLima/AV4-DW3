package com.autobots.automanager.filtros;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.autobots.automanager.jwt.ProvedorJwt;

class AutenticadorJwt {
	private String jwt;
	private ProvedorJwt provedorJwt;
	private UserDetailsService servico;

	public AutenticadorJwt(String jwt, ProvedorJwt provedorJwt, UserDetailsService servico) {
		this.jwt = jwt;
		this.provedorJwt = provedorJwt;
		this.servico = servico;
	}

	// null quando o token é inválido, a credencial foi excluída ou inativada, ou o usuário perdeu
	// todos os perfis de acesso depois de receber o token
	public UsernamePasswordAuthenticationToken obterAutenticacao() {
		if (!provedorJwt.validarJwt(jwt)) {
			return null;
		}
		try {
			UserDetails usuario = servico.loadUserByUsername(provedorJwt.obterNomeUsuario(jwt));
			if (!usuario.isEnabled()) {
				return null;
			}
			return new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
		} catch (UsernameNotFoundException e) {
			return null;
		}
	}
}
