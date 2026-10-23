package com.dextre.medishift.programacion.mensual;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-migracion-mensual", matches = "true")
class MigracionEspecialidadesPostgresqlTests extends SoporteMigracionMensual {

	@Test
	void v003EsAditivaYNoDeduceEspecialidadesDeLosNombres() throws Exception {
		migrar();
		ejecutarArchivo("V002__consultorios_por_area.sql");
		UUID unidad = nuevaUnidad(institucion, "DER");
		String profesionales = consultas.queryForObject("SELECT jsonb_agg(to_jsonb(p) ORDER BY id_professional)::text FROM professional p", String.class);
		String turnos = consultas.queryForObject("SELECT jsonb_agg(to_jsonb(t) ORDER BY id_shift)::text FROM shift t", String.class);
		ejecutarArchivo("V003__especialidades_por_area.sql");
		assertNull(consultas.queryForObject("SELECT id_specialty FROM planning_unit WHERE id_planning_unit=?", UUID.class, unidad));
		assertNull(consultas.queryForObject("SELECT id_institution FROM specialty WHERE id_specialty=?", UUID.class, especialidad));
		assertEquals(profesionales, consultas.queryForObject("SELECT jsonb_agg(to_jsonb(p) ORDER BY id_professional)::text FROM professional p", String.class));
		assertEquals(turnos, consultas.queryForObject("SELECT jsonb_agg(to_jsonb(t) ORDER BY id_shift)::text FROM shift t", String.class));
		ejecutarArchivo("U003__retirar_especialidades_por_area.sql");
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='planning_unit' AND column_name='id_specialty'", Integer.class));
	}

	@Test
	void u003RechazaPerderLaConfiguracionOLaPropiedadDeEspecialidades() throws Exception {
		migrar();
		ejecutarArchivo("V002__consultorios_por_area.sql");
		UUID unidad = nuevaUnidad(institucion, "DER");
		ejecutarArchivo("V003__especialidades_por_area.sql");
		consultas.update("UPDATE planning_unit SET id_specialty=? WHERE id_planning_unit=?", especialidad, unidad);
		var punto = conexion.setSavepoint();
		assertThrows(DataAccessException.class, () -> ejecutarArchivo("U003__retirar_especialidades_por_area.sql"));
		conexion.rollback(punto);
		assertEquals(especialidad, consultas.queryForObject("SELECT id_specialty FROM planning_unit WHERE id_planning_unit=?", UUID.class, unidad));
		consultas.update("UPDATE planning_unit SET id_specialty=NULL WHERE id_planning_unit=?", unidad);
		consultas.update("UPDATE specialty SET id_institution=? WHERE id_specialty=?", institucion, especialidad);
		var segundoPunto = conexion.setSavepoint();
		assertThrows(DataAccessException.class, () -> ejecutarArchivo("U003__retirar_especialidades_por_area.sql"));
		conexion.rollback(segundoPunto);
		assertEquals(institucion, consultas.queryForObject("SELECT id_institution FROM specialty WHERE id_specialty=?", UUID.class, especialidad));
	}
}
