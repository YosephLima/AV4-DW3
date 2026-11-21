package com.autobots.automanager.enumeracoes;

/**
 * Perfis da Tabela 1 da ATVIV (ADMINISTRADOR, GERENTE, VENDEDOR e CLIENTE), que substituem o
 * FUNCIONARIO do diagrama da ATVIII. FORNECEDOR continua como papel de negócio (quem fornece
 * mercadorias), mas não dá acesso à API: um usuário só com esse perfil não faz login.
 */
public enum PerfilUsuario {
	ADMINISTRADOR, GERENTE, VENDEDOR, CLIENTE, FORNECEDOR;

	// Quem pode registrar uma venda como funcionário
	public boolean ehFuncionario() {
		return this == ADMINISTRADOR || this == GERENTE || this == VENDEDOR;
	}

	// Quem pode fazer login
	public boolean daAcesso() {
		return this != FORNECEDOR;
	}
}
