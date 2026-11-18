-- Spec 001 · RNF-5 (T604): banco ampliado a 50 preguntas originales de cultura popular (cine, música, TV,
-- fiestas, comida). Respuestas tipo encuesta, ordenadas de mayor a menor; puntos por pregunta <= 100.

INSERT INTO question (id, text) VALUES (13, 'Menciona una película de Disney o Pixar');
INSERT INTO answer (question_id, text, points, position) VALUES
    (13, 'El Rey León', 28, 1),
    (13, 'Toy Story', 22, 2),
    (13, 'Coco', 17, 3),
    (13, 'Frozen', 13, 4),
    (13, 'La Sirenita', 9, 5),
    (13, 'Buscando a Nemo', 6, 6);

INSERT INTO question (id, text) VALUES (14, 'Menciona un superhéroe');
INSERT INTO answer (question_id, text, points, position) VALUES
    (14, 'Hombre Araña / Spider-Man', 30, 1),
    (14, 'Batman', 24, 2),
    (14, 'Superman', 18, 3),
    (14, 'Iron Man', 11, 4),
    (14, 'Capitán América', 8, 5),
    (14, 'Mujer Maravilla', 5, 6);

INSERT INTO question (id, text) VALUES (15, 'Menciona un cantante o grupo mexicano famoso');
INSERT INTO answer (question_id, text, points, position) VALUES
    (15, 'Luis Miguel', 27, 1),
    (15, 'Vicente Fernández', 21, 2),
    (15, 'Juan Gabriel', 18, 3),
    (15, 'Maná', 12, 4),
    (15, 'Peso Pluma', 9, 5),
    (15, 'Thalía', 6, 6);

INSERT INTO question (id, text) VALUES (16, 'Menciona una canción que no puede faltar en una boda');
INSERT INTO answer (question_id, text, points, position) VALUES
    (16, 'Payaso de rodeo', 26, 1),
    (16, 'La Macarena', 20, 2),
    (16, 'Mi gran noche', 16, 3),
    (16, 'El sonidito', 12, 4),
    (16, 'Danza kuduro', 9, 5),
    (16, 'Tiburón', 6, 6);

INSERT INTO question (id, text) VALUES (17, 'Menciona un programa de televisión que veías de niño');
INSERT INTO answer (question_id, text, points, position) VALUES
    (17, 'El Chavo del 8', 32, 1),
    (17, 'Plaza Sésamo', 18, 2),
    (17, 'Dragon Ball', 15, 3),
    (17, 'Los Simpson', 13, 4),
    (17, 'Bob Esponja', 9, 5),
    (17, 'En familia con Chabelo', 7, 6);

INSERT INTO question (id, text) VALUES (18, 'Menciona un personaje de El Chavo del 8');
INSERT INTO answer (question_id, text, points, position) VALUES
    (18, 'El Chavo', 25, 1),
    (18, 'Don Ramón', 22, 2),
    (18, 'La Chilindrina', 16, 3),
    (18, 'Quico', 14, 4),
    (18, 'Doña Florinda', 10, 5),
    (18, 'El Profesor Jirafales', 7, 6);

INSERT INTO question (id, text) VALUES (19, 'Menciona una telenovela mexicana famosa');
INSERT INTO answer (question_id, text, points, position) VALUES
    (19, 'María la del Barrio', 24, 1),
    (19, 'Rebelde', 20, 2),
    (19, 'Carrusel', 15, 3),
    (19, 'Corazón salvaje', 12, 4),
    (19, 'La usurpadora', 10, 5),
    (19, 'Amigos x siempre', 7, 6);

INSERT INTO question (id, text) VALUES (20, 'Menciona una caricatura que todo mundo conoce');
INSERT INTO answer (question_id, text, points, position) VALUES
    (20, 'Los Simpson', 28, 1),
    (20, 'Bob Esponja', 22, 2),
    (20, 'Tom y Jerry', 18, 3),
    (20, 'Dragon Ball', 12, 4),
    (20, 'Los Picapiedra', 9, 5),
    (20, 'Scooby-Doo', 6, 6);

INSERT INTO question (id, text) VALUES (21, 'Menciona un equipo de fútbol mexicano');
INSERT INTO answer (question_id, text, points, position) VALUES
    (21, 'América', 31, 1),
    (21, 'Chivas', 26, 2),
    (21, 'Cruz Azul', 16, 3),
    (21, 'Pumas', 12, 4),
    (21, 'Tigres', 7, 5),
    (21, 'Monterrey', 5, 6);

INSERT INTO question (id, text) VALUES (22, 'Menciona algo típico de las fiestas patrias');
INSERT INTO answer (question_id, text, points, position) VALUES
    (22, 'El Grito / ¡Viva México!', 27, 1),
    (22, 'Pozole', 22, 2),
    (22, 'Banderas', 16, 3),
    (22, 'Mariachi', 12, 4),
    (22, 'Fuegos artificiales', 9, 5),
    (22, 'Tequila', 7, 6);

INSERT INTO question (id, text) VALUES (23, 'Menciona algo que pones en una ofrenda de Día de Muertos');
INSERT INTO answer (question_id, text, points, position) VALUES
    (23, 'Flor de cempasúchil', 26, 1),
    (23, 'Pan de muerto', 22, 2),
    (23, 'Fotos', 18, 3),
    (23, 'Veladoras', 13, 4),
    (23, 'Calaveritas de azúcar', 9, 5),
    (23, 'Papel picado', 7, 6);

INSERT INTO question (id, text) VALUES (24, 'Menciona algo que no falta en Navidad');
INSERT INTO answer (question_id, text, points, position) VALUES
    (24, 'Árbol', 26, 1),
    (24, 'Regalos', 22, 2),
    (24, 'Santa Claus', 16, 3),
    (24, 'Posadas', 13, 4),
    (24, 'Nacimiento', 10, 5),
    (24, 'Ponche', 7, 6);

INSERT INTO question (id, text) VALUES (25, 'Menciona un dulce mexicano');
INSERT INTO answer (question_id, text, points, position) VALUES
    (25, 'Mazapán', 27, 1),
    (25, 'Pulparindo', 19, 2),
    (25, 'Duvalín', 15, 3),
    (25, 'Glorias', 12, 4),
    (25, 'Cocada', 9, 5),
    (25, 'Obleas', 6, 6);

INSERT INTO question (id, text) VALUES (26, 'Menciona un antojito mexicano');
INSERT INTO answer (question_id, text, points, position) VALUES
    (26, 'Tacos', 28, 1),
    (26, 'Quesadillas', 21, 2),
    (26, 'Tamales', 17, 3),
    (26, 'Sopes', 12, 4),
    (26, 'Tlacoyos', 9, 5),
    (26, 'Elotes / esquites', 7, 6);

INSERT INTO question (id, text) VALUES (27, 'Menciona una película mexicana famosa');
INSERT INTO answer (question_id, text, points, position) VALUES
    (27, 'Nosotros los Nobles', 22, 1),
    (27, 'No se aceptan devoluciones', 20, 2),
    (27, 'Amores perros', 15, 3),
    (27, 'El crimen del padre Amaro', 12, 4),
    (27, 'Y tu mamá también', 10, 5),
    (27, 'La dictadura perfecta', 8, 6);

INSERT INTO question (id, text) VALUES (28, 'Menciona a un actor o actriz de Hollywood');
INSERT INTO answer (question_id, text, points, position) VALUES
    (28, 'Brad Pitt', 23, 1),
    (28, 'Leonardo DiCaprio', 21, 2),
    (28, 'Tom Cruise', 16, 3),
    (28, 'Johnny Depp', 13, 4),
    (28, 'Jennifer Aniston', 9, 5),
    (28, 'Salma Hayek', 8, 6);

INSERT INTO question (id, text) VALUES (29, 'Menciona una película de terror');
INSERT INTO answer (question_id, text, points, position) VALUES
    (29, 'El exorcista', 26, 1),
    (29, 'El conjuro', 20, 2),
    (29, 'It (Eso)', 17, 3),
    (29, 'Chucky', 13, 4),
    (29, 'La monja', 9, 5),
    (29, 'Scream', 7, 6);

INSERT INTO question (id, text) VALUES (30, 'Menciona una saga de películas');
INSERT INTO answer (question_id, text, points, position) VALUES
    (30, 'Harry Potter', 28, 1),
    (30, 'Star Wars', 22, 2),
    (30, 'Rápidos y furiosos', 16, 3),
    (30, 'Avengers', 13, 4),
    (30, 'Shrek', 9, 5),
    (30, 'Jurassic Park', 6, 6);

INSERT INTO question (id, text) VALUES (31, 'Menciona una red social o app que usas diario');
INSERT INTO answer (question_id, text, points, position) VALUES
    (31, 'WhatsApp', 32, 1),
    (31, 'Facebook', 24, 2),
    (31, 'TikTok', 17, 3),
    (31, 'Instagram', 13, 4),
    (31, 'YouTube', 8, 5),
    (31, 'X / Twitter', 3, 6);

INSERT INTO question (id, text) VALUES (32, 'Menciona un videojuego famoso');
INSERT INTO answer (question_id, text, points, position) VALUES
    (32, 'Mario Bros', 29, 1),
    (32, 'Fortnite', 19, 2),
    (32, 'Minecraft', 16, 3),
    (32, 'FIFA', 14, 4),
    (32, 'Pac-Man', 9, 5),
    (32, 'Free Fire', 6, 6);

INSERT INTO question (id, text) VALUES (33, 'Menciona un personaje de Mario Bros');
INSERT INTO answer (question_id, text, points, position) VALUES
    (33, 'Mario', 32, 1),
    (33, 'Luigi', 24, 2),
    (33, 'Bowser', 15, 3),
    (33, 'Princesa Peach', 12, 4),
    (33, 'Yoshi', 9, 5),
    (33, 'Toad', 4, 6);

INSERT INTO question (id, text) VALUES (34, 'Menciona algo que se grita en un partido de fútbol');
INSERT INTO answer (question_id, text, points, position) VALUES
    (34, '¡Gol!', 38, 1),
    (34, '¡Árbitro vendido!', 18, 2),
    (34, '¡Fuera!', 13, 3),
    (34, '¡Sí se puede!', 12, 4),
    (34, '¡Penal!', 8, 5),
    (34, '¡Olé!', 6, 6);

INSERT INTO question (id, text) VALUES (35, 'Menciona un género de música que se baila en las fiestas');
INSERT INTO answer (question_id, text, points, position) VALUES
    (35, 'Cumbia', 31, 1),
    (35, 'Reguetón', 22, 2),
    (35, 'Salsa', 15, 3),
    (35, 'Banda', 13, 4),
    (35, 'Quebradita', 7, 5),
    (35, 'Merengue', 6, 6);

INSERT INTO question (id, text) VALUES (36, 'Menciona algo que hace la gente en Año Nuevo');
INSERT INTO answer (question_id, text, points, position) VALUES
    (36, 'Comer las 12 uvas', 31, 1),
    (36, 'Abrazar a la familia', 18, 2),
    (36, 'Usar ropa interior de color', 15, 3),
    (36, 'Salir con una maleta', 12, 4),
    (36, 'Brindar', 10, 5),
    (36, 'Hacer propósitos', 7, 6);

INSERT INTO question (id, text) VALUES (37, 'Menciona una canción infantil');
INSERT INTO answer (question_id, text, points, position) VALUES
    (37, 'Los pollitos dicen', 24, 1),
    (37, 'La vaca Lola', 18, 2),
    (37, 'Pin Pon', 16, 3),
    (37, 'Las mañanitas', 13, 4),
    (37, 'Estrellita, ¿dónde estás?', 11, 5),
    (37, 'Un elefante se columpiaba', 8, 6);

INSERT INTO question (id, text) VALUES (38, 'Menciona un cantante de reguetón');
INSERT INTO answer (question_id, text, points, position) VALUES
    (38, 'Bad Bunny', 34, 1),
    (38, 'Daddy Yankee', 22, 2),
    (38, 'J Balvin', 14, 3),
    (38, 'Karol G', 12, 4),
    (38, 'Maluma', 8, 5),
    (38, 'Wisin y Yandel', 5, 6);

INSERT INTO question (id, text) VALUES (39, 'Menciona una banda de rock en español');
INSERT INTO answer (question_id, text, points, position) VALUES
    (39, 'Maná', 27, 1),
    (39, 'Caifanes', 17, 2),
    (39, 'Soda Stereo', 16, 3),
    (39, 'Café Tacvba', 13, 4),
    (39, 'El Tri', 11, 5),
    (39, 'Molotov', 7, 6);

INSERT INTO question (id, text) VALUES (40, 'Menciona algo que no falta en una carne asada');
INSERT INTO answer (question_id, text, points, position) VALUES
    (40, 'Carne', 28, 1),
    (40, 'Cerveza', 22, 2),
    (40, 'Tortillas', 15, 3),
    (40, 'Salsa / guacamole', 13, 4),
    (40, 'Cebollitas', 9, 5),
    (40, 'Música', 6, 6);

INSERT INTO question (id, text) VALUES (41, 'Menciona un personaje de Los Simpson');
INSERT INTO answer (question_id, text, points, position) VALUES
    (41, 'Homero', 35, 1),
    (41, 'Bart', 22, 2),
    (41, 'Marge', 14, 3),
    (41, 'Lisa', 11, 4),
    (41, 'Maggie', 6, 5),
    (41, 'El Señor Burns', 5, 6);

INSERT INTO question (id, text) VALUES (42, 'Menciona algo que hace la gente en el cine');
INSERT INTO answer (question_id, text, points, position) VALUES
    (42, 'Comer palomitas', 36, 1),
    (42, 'Ver la película', 18, 2),
    (42, 'Dormirse', 13, 3),
    (42, 'Ver el celular', 11, 4),
    (42, 'Besarse', 9, 5),
    (42, 'Ir al baño', 6, 6);

INSERT INTO question (id, text) VALUES (43, 'Menciona una princesa de Disney');
INSERT INTO answer (question_id, text, points, position) VALUES
    (43, 'Cenicienta', 27, 1),
    (43, 'Blanca Nieves', 20, 2),
    (43, 'Bella', 15, 3),
    (43, 'Ariel', 13, 4),
    (43, 'Jasmín', 9, 5),
    (43, 'Mulán', 6, 6);

INSERT INTO question (id, text) VALUES (44, 'Menciona algo que compras en la tiendita de la esquina');
INSERT INTO answer (question_id, text, points, position) VALUES
    (44, 'Refresco', 30, 1),
    (44, 'Papitas', 22, 2),
    (44, 'Pan', 14, 3),
    (44, 'Leche', 12, 4),
    (44, 'Huevo', 9, 5),
    (44, 'Tortillas', 6, 6);

INSERT INTO question (id, text) VALUES (45, 'Menciona un apodo cariñoso para tu pareja');
INSERT INTO answer (question_id, text, points, position) VALUES
    (45, 'Amor', 33, 1),
    (45, 'Gordo / gorda', 20, 2),
    (45, 'Mi vida', 15, 3),
    (45, 'Bebé', 12, 4),
    (45, 'Cielo', 9, 5),
    (45, 'Corazón', 6, 6);

INSERT INTO question (id, text) VALUES (46, 'Menciona una frase típica de las mamás mexicanas');
INSERT INTO answer (question_id, text, points, position) VALUES
    (46, '¿Ya comiste?', 22, 1),
    (46, 'Te lo dije', 20, 2),
    (46, 'Cuando llegue tu papá…', 16, 3),
    (46, 'No es no', 12, 4),
    (46, 'Abrígate', 10, 5),
    (46, '¿Y si tus amigos se avientan de un puente?', 8, 6);

INSERT INTO question (id, text) VALUES (47, 'Menciona a un luchador mexicano');
INSERT INTO answer (question_id, text, points, position) VALUES
    (47, 'El Santo', 31, 1),
    (47, 'Blue Demon', 19, 2),
    (47, 'Místico', 15, 3),
    (47, 'Rey Mysterio', 14, 4),
    (47, 'Mil Máscaras', 9, 5),
    (47, 'Octagón', 6, 6);

INSERT INTO question (id, text) VALUES (48, 'Menciona un juego de mesa');
INSERT INTO answer (question_id, text, points, position) VALUES
    (48, 'Lotería', 29, 1),
    (48, 'Turista / Monopoly', 22, 2),
    (48, 'Serpientes y escaleras', 16, 3),
    (48, 'Dominó', 13, 4),
    (48, 'UNO', 10, 5),
    (48, 'Ajedrez', 5, 6);

INSERT INTO question (id, text) VALUES (49, 'Menciona una carta de la lotería mexicana');
INSERT INTO answer (question_id, text, points, position) VALUES
    (49, 'El Diablito', 22, 1),
    (49, 'La Sirena', 18, 2),
    (49, 'El Catrín', 16, 3),
    (49, 'La Muerte', 13, 4),
    (49, 'El Borracho', 11, 5),
    (49, 'La Dama', 8, 6);

INSERT INTO question (id, text) VALUES (50, 'Menciona un cantante o grupo en inglés que todo mundo conoce');
INSERT INTO answer (question_id, text, points, position) VALUES
    (50, 'Michael Jackson', 27, 1),
    (50, 'The Beatles', 20, 2),
    (50, 'Queen', 15, 3),
    (50, 'Taylor Swift', 13, 4),
    (50, 'Madonna', 9, 5),
    (50, 'Bruno Mars', 7, 6);
