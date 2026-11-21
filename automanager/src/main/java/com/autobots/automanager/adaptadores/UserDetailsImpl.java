package com.autobots.automanager.adaptadores;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.autobots.automanager.entidades.CredencialUsuarioSenha;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.enumeracoes.PerfilUsuario;

/**
 * Usuário autenticado. Guarda uma cópia do id e dos perfis para as regras de autorização não
 * dependerem da entidade (e da sessão do JPA) durante a requisição.
 */
@SuppressWarnings("serial")
public class UserDetailsImpl implements UserDetails {
	private final Long id;
	private final String nomeUsuario;
	private final String senha;
	private final boolean credencialAtiva;
	private final Set<PerfilUsuario> perfis;

	public UserDetailsImpl(Usuario usuario, CredencialUsuarioSenha credencial) {
		this.id = usuario.getId();
		this.nomeUsuario = credencial.getNomeUsuario();
		this.senha = credencial.getSenha();
		this.credencialAtiva = !Boolean.TRUE.equals(credencial.getInativo());
		this.perfis = usuario.getPerfis().isEmpty() ? EnumSet.noneOf(PerfilUsuario.class)
				: EnumSet.copyOf(usuario.getPerfis());
	}

	public Long getId() {
		return id;
	}

	public Set<PerfilUsuario> getPerfis() {
		return perfis;
	}

	// O Spring Security espera o prefixo ROLE_ nas autoridades. O enum guarda o nome de negócio
	// (ADMINISTRADOR, GERENTE...) e o prefixo só aparece aqui
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		List<SimpleGrantedAuthority> autoridades = perfis.stream().filter(PerfilUsuario::daAcesso)
				.map(perfil -> new SimpleGrantedAuthority("ROLE_" + perfil.name())).collect(Collectors.toList());
		return autoridades;
	}

	@Override
	public String getPassword() {
		return senha;
	}

	@Override
	public String getUsername() {
		return nomeUsuario;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	// Credencial inativa ou usuário sem nenhum perfil de acesso (ex.: só FORNECEDOR) não entram
	@Override
	public boolean isEnabled() {
		return credencialAtiva && perfis.stream().anyMatch(PerfilUsuario::daAcesso);
	}
}
