package com.dextre.medishift.programacion.mensual;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-migracion-mensual", matches = "true")
class MigracionEtiquetasInstitucionalesPostgresqlTests extends SoporteMigracionMensual {

	@Test
	void conservaAsignacionesYNombresHistoricosEntreClinicas() throws Exception {
		migrar();
		ejecutarArchivo("V002__consultorios_por_area.sql");
		ejecutarArchivo("V003__especialidades_por_area.sql");
		ejecutarArchivo("V006__pacientes_citas.sql");
		ejecutarArchivo("V008__etiquetas_y_subareas.sql");
		UUID otraClinica = nuevaInstitucion();
		UUID otraCuenta = nuevoActor(otraClinica);
		UUID otraPersona = UUID.randomUUID();
		UUID otraSala = UUID.randomUUID();
		UUID otraArea = nuevaUnidad(otraClinica, "AREA-02");
		UUID otroPlan = UUID.randomUUID();
		UUID otroTurno = UUID.randomUUID();
		UUID cobertura = UUID.randomUUID();
		UUID historial = UUID.randomUUID();
		consultas.update("""
				INSERT INTO specialty (id_specialty,code_specialty,name_specialty,id_institution)
				VALUES (gen_random_uuid(),'PROPIA','Especialidad de ensayo',?)
				""", otraClinica);
		consultas.update("""
				INSERT INTO professional (id_professional,id_institution,first_name_professional,last_name_professional,
				license_number_professional,category_professional,email_professional,status_professional)
				VALUES (?,?,'Otra','Persona','P-02','Médico','otra@medishift.test','active')
				""", otraPersona, otraClinica);
		consultas.update("INSERT INTO professional_specialty VALUES (?,?,true,true)", otraPersona, especialidad);
		consultas.update("UPDATE planning_unit SET id_specialty=? WHERE id_planning_unit=?", especialidad, otraArea);
		consultas.update("""
				INSERT INTO room (id_room,id_institution,code_room,name_room,location_room,is_general_room,status_room,id_planning_unit)
				VALUES (?,?,'R-02','Consultorio de ensayo','Piso 2',false,'active',?)
				""", otraSala, otraClinica, otraArea);
		consultas.update("INSERT INTO room_specialty VALUES (?,?)", otraSala, especialidad);
		consultas.update("""
				INSERT INTO schedule (id_schedule,id_institution,period_start_schedule,period_end_schedule,version_schedule,
				status_schedule,id_creator_user_account,period_type_schedule,id_planning_unit)
				VALUES (?,?,'2032-11-01','2032-11-30',1,'draft',?,'monthly',?)
				""", otroPlan, otraClinica, otraCuenta, otraArea);
		consultas.update("""
				INSERT INTO shift (id_shift,id_institution,id_schedule,id_professional,id_room,id_specialty,
				start_at_shift,end_at_shift,status_shift)
				VALUES (?,?,?,?,?,?,'2032-11-02 09:00-05','2032-11-02 12:00-05','draft')
				""", otroTurno, otraClinica, otroPlan, otraPersona, otraSala, especialidad);
		consultas.update("UPDATE shift SET nombre_etiqueta='Nombre del turno al registrar' WHERE id_shift=?", otroTurno);
		consultas.update("""
				INSERT INTO coverage_requirement VALUES (?,?,?,'2032-11-02 09:00-05','2032-11-02 12:00-05',1,NULL)
				""", cobertura, otroPlan, especialidad);
		consultas.update("""
				INSERT INTO historial_plan_mensual (id,id_institucion,id_plan,version,contenido,id_actor)
				VALUES (?,?,?,1,jsonb_build_object('turnos',jsonb_build_array(jsonb_build_object('id_specialty',?::text))),?)
				""", historial, otraClinica, otroPlan, especialidad, otraCuenta);

		ejecutarArchivo("V009__etiquetas_institucionales.sql");

		assertEquals(institucion, obtenerId("SELECT id_institution FROM specialty WHERE id_specialty=?", especialidad));
		UUID copia = obtenerId("SELECT id_specialty FROM professional_specialty WHERE id_professional=? AND activo", otraPersona);
		assertNotEquals(especialidad, copia);
		assertEquals(otraClinica, obtenerId("SELECT id_institution FROM specialty WHERE id_specialty=?", copia));
		assertEquals(copia, obtenerId("SELECT id_specialty FROM room_specialty WHERE id_room=?", otraSala));
		assertEquals(copia, obtenerId("SELECT id_etiqueta FROM area_etiqueta WHERE id_area=?", otraArea));
		assertEquals(copia, obtenerId("SELECT id_specialty FROM planning_unit WHERE id_planning_unit=?", otraArea));
		assertEquals(copia, obtenerId("SELECT id_specialty FROM etiqueta_consultorio_programable WHERE id_room=?", otraSala));
		assertEquals(copia, obtenerId("SELECT id_specialty FROM shift WHERE id_shift=?", otroTurno));
		assertEquals(copia, obtenerId("SELECT id_specialty FROM coverage_requirement WHERE id_coverage_requirement=?", cobertura));
		assertEquals("Nombre del turno al registrar", consultas.queryForObject(
				"SELECT nombre_etiqueta FROM shift WHERE id_shift=?", String.class, otroTurno));
		assertEquals("Especialidad de ensayo", consultas.queryForObject("""
				SELECT contenido #>> '{turnos,0,nombre_etiqueta}' FROM historial_plan_mensual WHERE id=?
				""", String.class, historial));
		rechaza("23502", () -> consultas.update("""
				INSERT INTO specialty (id_specialty,code_specialty,name_specialty) VALUES (gen_random_uuid(),'SIN','Sin dueño')
				"""));
		rechaza("23514", () -> consultas.update("INSERT INTO room_specialty VALUES (?,?)", otraSala, especialidad));
		rechaza("23514", () -> consultas.update("UPDATE coverage_requirement SET id_specialty=? WHERE id_coverage_requirement=?",
				especialidad, cobertura));
	}

	private UUID obtenerId(String sql, UUID id) {
		return consultas.queryForObject(sql, UUID.class, id);
	}
}
