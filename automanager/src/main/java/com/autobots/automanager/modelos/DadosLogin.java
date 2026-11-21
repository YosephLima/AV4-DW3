package com.autobots.automanager.modelos;

import lombok.Getter;
import lombok.Setter;

// Corpo do POST /login
@Getter
@Setter
public class DadosLogin {
	private String nomeUsuario;
	private String senha;
}
