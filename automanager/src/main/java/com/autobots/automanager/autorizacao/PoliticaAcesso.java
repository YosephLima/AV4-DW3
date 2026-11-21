package com.autobots.automanager.autorizacao;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.autobots.automanager.adaptadores.UserDetailsImpl;
import com.autobots.automanager.entidades.Empresa;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.entidades.Veiculo;
import com.autobots.automanager.entidades.Venda;
import com.autobots.automanager.enumeracoes.PerfilUsuario;
import com.autobots.automanager.modelos.CadastroVenda;
import com.autobots.automanager.repositorios.RepositorioEmpresa;
import com.autobots.automanager.repositorios.RepositorioUsuario;
import com.autobots.automanager.repositorios.RepositorioVeiculo;
import com.autobots.automanager.repositorios.RepositorioVenda;

/**
 * Regras da Tabela 1 que dependem dos dados. Os controles as usam nas expressões de
 * {@code @PreAuthorize("@acesso.podeLerUsuario(#id)")} e para filtrar as listagens.
 *
 * <ul>
 * <li>ADMINISTRADOR: tudo, inclusive administradores e fornecedores.</li>
 * <li>GERENTE: CRUD de usuários GERENTE, VENDEDOR e CLIENTE; CRUD de serviços, mercadorias e vendas.</li>
 * <li>VENDEDOR: CRUD de usuários CLIENTE; lê serviços e mercadorias; cria as vendas em que ele é o
 * funcionário e lê essas vendas.</li>
 * <li>CLIENTE: lê o próprio cadastro e as vendas em que foi o cliente.</li>
 * </ul>
 *
 * Gerenciar um usuário vale também para os dados dele (telefones, documentos, emails, endereço,
 * credenciais e veículos). Um alvo com vários perfis só é gerenciável se todos forem: o gerente
 * não altera quem é GERENTE e ADMINISTRADOR ao mesmo tempo. Ao cadastrar ou alterar, os perfis
 * atribuídos também precisam ser gerenciáveis, então ninguém promove um usuário acima do que
 * poderia gerenciar.
 *
 * Quando o registro não existe, só quem enxergaria qualquer registro daquele tipo passa (e o
 * controle responde 404). Os demais recebem 403, para a API não revelar quais ids existem.
 */
@Component("acesso")
public class PoliticaAcesso {
	private static final Set<PerfilUsuario> GERENCIADOS_PELO_GERENTE = EnumSet.of(PerfilUsuario.GERENTE,
			PerfilUsuario.VENDEDOR, PerfilUsuario.CLIENTE);
	private static final Set<PerfilUsuario> GERENCIADOS_PELO_VENDEDOR = EnumSet.of(PerfilUsuario.CLIENTE);

	@Autowired
	private RepositorioUsuario repositorioUsuario;
	@Autowired
	private RepositorioEmpresa repositorioEmpresa;
	@Autowired
	private RepositorioVeiculo repositorioVeiculo;
	@Autowired
	private RepositorioVenda repositorioVenda;

	// ------------------------------------------------------------ quem está logado

	private Optional<UserDetailsImpl> atual() {
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacao != null && autenticacao.getPrincipal() instanceof UserDetailsImpl) {
			return Optional.of((UserDetailsImpl) autenticacao.getPrincipal());
		}
		return Optional.empty();
	}

	private boolean tem(PerfilUsuario perfil) {
		return atual().map(usuario -> usuario.getPerfis().contains(perfil)).orElse(false);
	}

	private boolean ehAdministrador() {
		return tem(PerfilUsuario.ADMINISTRADOR);
	}

	private boolean ehProprio(Usuario usuario) {
		return atual().map(eu -> eu.getId().equals(usuario.getId())).orElse(false);
	}

	private boolean ehProprio(Long id) {
		return id != null && atual().map(eu -> eu.getId().equals(id)).orElse(false);
	}

	// ------------------------------------------------------------ usuários

	// Perfis que o usuário logado pode gerenciar e atribuir (a união dos seus perfis)
	private Set<PerfilUsuario> gerenciaveis() {
		if (ehAdministrador()) {
			return EnumSet.allOf(PerfilUsuario.class);
		}
		Set<PerfilUsuario> perfis = EnumSet.noneOf(PerfilUsuario.class);
		if (tem(PerfilUsuario.GERENTE)) {
			perfis.addAll(GERENCIADOS_PELO_GERENTE);
		}
		if (tem(PerfilUsuario.VENDEDOR)) {
			perfis.addAll(GERENCIADOS_PELO_VENDEDOR);
		}
		return perfis;
	}

	public boolean podeAtribuirPerfis(Collection<PerfilUsuario> perfis) {
		if (ehAdministrador()) {
			return true;
		}
		return perfis != null && !perfis.isEmpty() && gerenciaveis().containsAll(perfis);
	}

	public boolean podeGerenciar(Usuario usuario) {
		return ehAdministrador() || podeAtribuirPerfis(usuario.getPerfis());
	}

	// Todo usuário lê o próprio cadastro
	public boolean podeLer(Usuario usuario) {
		return ehProprio(usuario) || podeGerenciar(usuario);
	}

	public boolean podeLerUsuario(long id) {
		return repositorioUsuario.findById(id).map(this::podeLer).orElse(ehAdministrador());
	}

	public boolean podeGerenciarUsuario(long id) {
		return repositorioUsuario.findById(id).map(this::podeGerenciar).orElse(ehAdministrador());
	}

	// PUT /usuarios/{id}: o alvo precisa ser gerenciável e, se vierem perfis novos, eles também
	public boolean podeAlterarUsuario(long id, Usuario atualizacao) {
		if (!podeGerenciarUsuario(id)) {
			return false;
		}
		boolean trocaPerfis = atualizacao != null && atualizacao.getPerfis() != null && !atualizacao.getPerfis().isEmpty();
		return !trocaPerfis || podeAtribuirPerfis(atualizacao.getPerfis());
	}

	// ------------------------------------------------------------ dados embutidos do usuário ou da empresa

	// Telefone e endereço podem ser da empresa: só o administrador altera a empresa, e quem
	// trabalha nela (administrador, gerente e vendedor) a consulta
	private boolean podeLerEmpresa() {
		return tem(PerfilUsuario.ADMINISTRADOR) || tem(PerfilUsuario.GERENTE) || tem(PerfilUsuario.VENDEDOR);
	}

	private boolean regraDoDono(Optional<Usuario> usuario, Optional<Empresa> empresa, boolean leitura) {
		if (usuario.isPresent()) {
			return leitura ? podeLer(usuario.get()) : podeGerenciar(usuario.get());
		}
		if (empresa.isPresent()) {
			return leitura ? podeLerEmpresa() : ehAdministrador();
		}
		return ehAdministrador();
	}

	public boolean podeLerTelefone(long id) {
		return regraDoDono(repositorioUsuario.findByTelefonesId(id), repositorioEmpresa.findByTelefonesId(id), true);
	}

	public boolean podeGerenciarTelefone(long id) {
		return regraDoDono(repositorioUsuario.findByTelefonesId(id), repositorioEmpresa.findByTelefonesId(id), false);
	}

	public boolean podeLerEndereco(long id) {
		return regraDoDono(repositorioUsuario.findByEnderecoId(id), repositorioEmpresa.findByEnderecoId(id), true);
	}

	public boolean podeGerenciarEndereco(long id) {
		return regraDoDono(repositorioUsuario.findByEnderecoId(id), repositorioEmpresa.findByEnderecoId(id), false);
	}

	public boolean podeLerDocumento(long id) {
		return regraDoDono(repositorioUsuario.findByDocumentosId(id), Optional.empty(), true);
	}

	public boolean podeGerenciarDocumento(long id) {
		return regraDoDono(repositorioUsuario.findByDocumentosId(id), Optional.empty(), false);
	}

	public boolean podeLerEmail(long id) {
		return regraDoDono(repositorioUsuario.findByEmailsId(id), Optional.empty(), true);
	}

	public boolean podeGerenciarEmail(long id) {
		return regraDoDono(repositorioUsuario.findByEmailsId(id), Optional.empty(), false);
	}

	public boolean podeLerCredencial(long id) {
		return regraDoDono(repositorioUsuario.findByCredenciaisId(id), Optional.empty(), true);
	}

	public boolean podeGerenciarCredencial(long id) {
		return regraDoDono(repositorioUsuario.findByCredenciaisId(id), Optional.empty(), false);
	}

	// ------------------------------------------------------------ veículos (seguem o proprietário)

	public boolean podeLer(Veiculo veiculo) {
		return veiculo.getProprietario() == null ? ehAdministrador() : podeLer(veiculo.getProprietario());
	}

	public boolean podeLerVeiculo(long id) {
		return repositorioVeiculo.findById(id).map(this::podeLer).orElse(ehAdministrador());
	}

	public boolean podeGerenciarVeiculo(long id) {
		return repositorioVeiculo.findById(id)
				.map(veiculo -> veiculo.getProprietario() == null ? ehAdministrador() : podeGerenciar(veiculo.getProprietario()))
				.orElse(ehAdministrador());
	}

	// ------------------------------------------------------------ vendas

	private boolean vendaGlobal() {
		return tem(PerfilUsuario.ADMINISTRADOR) || tem(PerfilUsuario.GERENTE);
	}

	public boolean podeLer(Venda venda) {
		if (vendaGlobal()) {
			return true;
		}
		if (tem(PerfilUsuario.VENDEDOR) && venda.getFuncionario() != null && ehProprio(venda.getFuncionario())) {
			return true;
		}
		return tem(PerfilUsuario.CLIENTE) && venda.getCliente() != null && ehProprio(venda.getCliente());
	}

	public boolean podeLerVenda(long id) {
		return repositorioVenda.findById(id).map(this::podeLer).orElse(vendaGlobal());
	}

	// O vendedor só registra vendas feitas por ele mesmo
	public boolean podeCadastrarVenda(CadastroVenda dados) {
		if (vendaGlobal()) {
			return true;
		}
		return tem(PerfilUsuario.VENDEDOR) && dados != null
				&& (dados.getFuncionarioId() == null || ehProprio(dados.getFuncionarioId()));
	}
}
