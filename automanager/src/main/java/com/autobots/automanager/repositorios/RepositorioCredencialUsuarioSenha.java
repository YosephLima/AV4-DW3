package com.autobots.automanager.repositorios;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.autobots.automanager.entidades.CredencialUsuarioSenha;

public interface RepositorioCredencialUsuarioSenha extends JpaRepository<CredencialUsuarioSenha, Long> {
	// O login procura direto pelo nome de usuário (único), em vez de percorrer todos os usuários
	Optional<CredencialUsuarioSenha> findByNomeUsuario(String nomeUsuario);
}
