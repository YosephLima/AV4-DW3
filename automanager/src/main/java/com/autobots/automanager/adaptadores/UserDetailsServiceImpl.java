package com.autobots.automanager.adaptadores;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autobots.automanager.entidades.CredencialUsuarioSenha;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.repositorios.RepositorioCredencialUsuarioSenha;
import com.autobots.automanager.repositorios.RepositorioUsuario;

/**
 * Carrega o usuário pela credencial de usuário e senha. É chamado no login e também a cada
 * requisição autenticada (o token só carrega o nome de usuário), então perfis alterados e
 * credenciais inativadas valem na hora, sem esperar o token expirar.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
	@Autowired
	private RepositorioCredencialUsuarioSenha repositorioCredencial;
	@Autowired
	private RepositorioUsuario repositorioUsuario;

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String nomeUsuario) throws UsernameNotFoundException {
		CredencialUsuarioSenha credencial = repositorioCredencial.findByNomeUsuario(nomeUsuario)
				.orElseThrow(() -> new UsernameNotFoundException(nomeUsuario));
		Usuario usuario = repositorioUsuario.findByCredenciaisId(credencial.getId())
				.orElseThrow(() -> new UsernameNotFoundException(nomeUsuario));
		return new UserDetailsImpl(usuario, credencial);
	}

	// Preenche o ultimoAcesso da credencial (campo do diagrama) a cada login bem-sucedido
	@Transactional
	public void registrarAcesso(String nomeUsuario) {
		repositorioCredencial.findByNomeUsuario(nomeUsuario).ifPresent(credencial -> {
			credencial.setUltimoAcesso(new Date());
			repositorioCredencial.save(credencial);
		});
	}
}
