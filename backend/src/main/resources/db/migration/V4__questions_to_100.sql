-- Spec 001 · RNF-5 (T605): banco ampliado a 100 preguntas originales de cultura popular (primera versión).
-- Respuestas tipo encuesta, ordenadas de mayor a menor; puntos por pregunta <= 100.

INSERT INTO question (id, text) VALUES (51, 'Menciona un personaje de Shrek');
INSERT INTO answer (question_id, text, points, position) VALUES
    (51, 'Shrek', 30, 1),
    (51, 'Burro', 26, 2),
    (51, 'Fiona', 17, 3),
    (51, 'El Gato con Botas', 13, 4),
    (51, 'Lord Farquaad', 6, 5),
    (51, 'Dragona', 4, 6);

INSERT INTO question (id, text) VALUES (52, 'Menciona un personaje de Harry Potter');
INSERT INTO answer (question_id, text, points, position) VALUES
    (52, 'Harry', 32, 1),
    (52, 'Hermione', 22, 2),
    (52, 'Ron', 16, 3),
    (52, 'Dumbledore', 12, 4),
    (52, 'Voldemort', 9, 5),
    (52, 'Hagrid', 5, 6);

INSERT INTO question (id, text) VALUES (53, 'Menciona un personaje de Star Wars');
INSERT INTO answer (question_id, text, points, position) VALUES
    (53, 'Darth Vader', 33, 1),
    (53, 'Luke Skywalker', 20, 2),
    (53, 'Yoda', 18, 3),
    (53, 'Chewbacca', 11, 4),
    (53, 'Han Solo', 8, 5),
    (53, 'R2-D2', 6, 6);

INSERT INTO question (id, text) VALUES (54, 'Menciona un villano de película');
INSERT INTO answer (question_id, text, points, position) VALUES
    (54, 'El Guasón / Joker', 29, 1),
    (54, 'Darth Vader', 21, 2),
    (54, 'Voldemort', 15, 3),
    (54, 'Thanos', 13, 4),
    (54, 'Chucky', 9, 5),
    (54, 'Freddy Krueger', 6, 6);

INSERT INTO question (id, text) VALUES (55, 'Menciona una película animada que hizo llorar a todos');
INSERT INTO answer (question_id, text, points, position) VALUES
    (55, 'Coco', 30, 1),
    (55, 'Up', 24, 2),
    (55, 'El Rey León', 18, 3),
    (55, 'Toy Story 3', 12, 4),
    (55, 'Intensa-Mente', 8, 5),
    (55, 'Bambi', 5, 6);

INSERT INTO question (id, text) VALUES (56, 'Menciona un actor o actriz mexicano famoso');
INSERT INTO answer (question_id, text, points, position) VALUES
    (56, 'Salma Hayek', 22, 1),
    (56, 'Gael García Bernal', 18, 2),
    (56, 'Diego Luna', 16, 3),
    (56, 'Eugenio Derbez', 15, 4),
    (56, 'Cantinflas', 12, 5),
    (56, 'Pedro Infante', 8, 6);

INSERT INTO question (id, text) VALUES (57, 'Menciona una cantante latina famosa');
INSERT INTO answer (question_id, text, points, position) VALUES
    (57, 'Shakira', 30, 1),
    (57, 'Thalía', 20, 2),
    (57, 'Jennifer Lopez', 15, 3),
    (57, 'Selena', 13, 4),
    (57, 'Karol G', 10, 5),
    (57, 'Gloria Trevi', 6, 6);

INSERT INTO question (id, text) VALUES (58, 'Menciona una canción de Juan Gabriel');
INSERT INTO answer (question_id, text, points, position) VALUES
    (58, 'Querida', 28, 1),
    (58, 'Amor eterno', 24, 2),
    (58, 'Hasta que te conocí', 15, 3),
    (58, 'Así fue', 12, 4),
    (58, 'No tengo dinero', 9, 5),
    (58, 'El Noa Noa', 6, 6);

INSERT INTO question (id, text) VALUES (59, 'Menciona una canción de Navidad');
INSERT INTO answer (question_id, text, points, position) VALUES
    (59, 'Pedir posada', 24, 1),
    (59, 'Noche de paz', 22, 2),
    (59, 'Los peces en el río', 16, 3),
    (59, 'Campana sobre campana', 14, 4),
    (59, 'Rodolfo el reno', 10, 5),
    (59, 'Feliz Navidad', 8, 6);

INSERT INTO question (id, text) VALUES (60, 'Menciona algo que hay en una posada');
INSERT INTO answer (question_id, text, points, position) VALUES
    (60, 'Piñata', 30, 1),
    (60, 'Ponche', 24, 2),
    (60, 'Aguinaldo / colación', 15, 3),
    (60, 'Velitas', 12, 4),
    (60, 'Tamales', 9, 5),
    (60, 'Villancicos', 6, 6);

INSERT INTO question (id, text) VALUES (61, 'Menciona un platillo de Navidad o Año Nuevo');
INSERT INTO answer (question_id, text, points, position) VALUES
    (61, 'Bacalao', 24, 1),
    (61, 'Pavo', 22, 2),
    (61, 'Romeritos', 16, 3),
    (61, 'Pierna', 14, 4),
    (61, 'Tamales', 10, 5),
    (61, 'Pozole', 7, 6);

INSERT INTO question (id, text) VALUES (62, 'Menciona una fruta');
INSERT INTO answer (question_id, text, points, position) VALUES
    (62, 'Manzana', 30, 1),
    (62, 'Plátano', 24, 2),
    (62, 'Mango', 15, 3),
    (62, 'Naranja', 12, 4),
    (62, 'Fresa', 9, 5),
    (62, 'Sandía', 6, 6);

INSERT INTO question (id, text) VALUES (63, 'Menciona un tipo de taco');
INSERT INTO answer (question_id, text, points, position) VALUES
    (63, 'Al pastor', 34, 1),
    (63, 'Suadero', 16, 2),
    (63, 'Bistec', 14, 3),
    (63, 'Carnitas', 13, 4),
    (63, 'Barbacoa', 10, 5),
    (63, 'De canasta', 7, 6);

INSERT INTO question (id, text) VALUES (64, 'Menciona algo que le pones a los chilaquiles');
INSERT INTO answer (question_id, text, points, position) VALUES
    (64, 'Crema', 28, 1),
    (64, 'Queso', 24, 2),
    (64, 'Cebolla', 16, 3),
    (64, 'Pollo', 13, 4),
    (64, 'Huevo', 9, 5),
    (64, 'Frijoles', 5, 6);

INSERT INTO question (id, text) VALUES (65, 'Menciona una marca de refresco');
INSERT INTO answer (question_id, text, points, position) VALUES
    (65, 'Coca-Cola', 40, 1),
    (65, 'Pepsi', 18, 2),
    (65, 'Sprite', 13, 3),
    (65, 'Fanta', 10, 4),
    (65, 'Jarritos', 8, 5),
    (65, 'Boing', 5, 6);

INSERT INTO question (id, text) VALUES (66, 'Menciona algo que desayunas en la calle');
INSERT INTO answer (question_id, text, points, position) VALUES
    (66, 'Tamales', 30, 1),
    (66, 'Atole', 20, 2),
    (66, 'Tortas', 16, 3),
    (66, 'Chilaquiles', 12, 4),
    (66, 'Pan dulce', 9, 5),
    (66, 'Licuado', 7, 6);

INSERT INTO question (id, text) VALUES (67, 'Menciona un pan dulce');
INSERT INTO answer (question_id, text, points, position) VALUES
    (67, 'Concha', 34, 1),
    (67, 'Cuerno', 18, 2),
    (67, 'Dona', 14, 3),
    (67, 'Oreja', 12, 4),
    (67, 'Polvorón', 9, 5),
    (67, 'Mantecada', 8, 6);

INSERT INTO question (id, text) VALUES (68, 'Menciona una mascota');
INSERT INTO answer (question_id, text, points, position) VALUES
    (68, 'Perro', 40, 1),
    (68, 'Gato', 30, 2),
    (68, 'Pez', 10, 3),
    (68, 'Hámster', 7, 4),
    (68, 'Tortuga', 6, 5),
    (68, 'Perico', 4, 6);

INSERT INTO question (id, text) VALUES (69, 'Menciona un animal de la selva');
INSERT INTO answer (question_id, text, points, position) VALUES
    (69, 'León', 30, 1),
    (69, 'Tigre', 20, 2),
    (69, 'Mono', 18, 3),
    (69, 'Elefante', 13, 4),
    (69, 'Jaguar', 9, 5),
    (69, 'Serpiente', 6, 6);

INSERT INTO question (id, text) VALUES (70, 'Menciona algo que hay en una feria o parque de diversiones');
INSERT INTO answer (question_id, text, points, position) VALUES
    (70, 'Montaña rusa', 34, 1),
    (70, 'Rueda de la fortuna', 22, 2),
    (70, 'Carrusel', 14, 3),
    (70, 'Carritos chocones', 12, 4),
    (70, 'Algodón de azúcar', 8, 5),
    (70, 'Casa del terror', 6, 6);

INSERT INTO question (id, text) VALUES (71, 'Menciona un lugar turístico de México');
INSERT INTO answer (question_id, text, points, position) VALUES
    (71, 'Cancún', 30, 1),
    (71, 'Acapulco', 20, 2),
    (71, 'Chichén Itzá', 16, 3),
    (71, 'Puerto Vallarta', 12, 4),
    (71, 'Teotihuacán', 10, 5),
    (71, 'Los Cabos', 6, 6);

INSERT INTO question (id, text) VALUES (72, 'Menciona un estado famoso por su comida');
INSERT INTO answer (question_id, text, points, position) VALUES
    (72, 'Oaxaca', 28, 1),
    (72, 'Puebla', 22, 2),
    (72, 'Yucatán', 16, 3),
    (72, 'Jalisco', 13, 4),
    (72, 'Ciudad de México', 9, 5),
    (72, 'Veracruz', 7, 6);

INSERT INTO question (id, text) VALUES (73, 'Menciona algo que haces en domingo');
INSERT INTO answer (question_id, text, points, position) VALUES
    (73, 'Descansar', 26, 1),
    (73, 'Ir a misa', 20, 2),
    (73, 'Comer en familia', 18, 3),
    (73, 'Ver el fútbol', 14, 4),
    (73, 'Ir al parque', 9, 5),
    (73, 'Lavar la ropa', 6, 6);

INSERT INTO question (id, text) VALUES (74, 'Menciona algo que haces en un día de lluvia');
INSERT INTO answer (question_id, text, points, position) VALUES
    (74, 'Dormir', 26, 1),
    (74, 'Ver películas', 22, 2),
    (74, 'Tomar café o chocolate', 18, 3),
    (74, 'Quedarte en casa', 14, 4),
    (74, 'Comer pan', 8, 5),
    (74, 'Jugar juegos de mesa', 5, 6);

INSERT INTO question (id, text) VALUES (75, 'Menciona una profesión u oficio');
INSERT INTO answer (question_id, text, points, position) VALUES
    (75, 'Doctor', 26, 1),
    (75, 'Maestro', 22, 2),
    (75, 'Policía', 15, 3),
    (75, 'Ingeniero', 13, 4),
    (75, 'Albañil', 10, 5),
    (75, 'Abogado', 7, 6);

INSERT INTO question (id, text) VALUES (76, 'Menciona algo que hay en la mochila de un estudiante');
INSERT INTO answer (question_id, text, points, position) VALUES
    (76, 'Cuadernos', 30, 1),
    (76, 'Lápiz / pluma', 24, 2),
    (76, 'Libros', 16, 3),
    (76, 'Lonche', 12, 4),
    (76, 'Calculadora', 8, 5),
    (76, 'Colores', 5, 6);

INSERT INTO question (id, text) VALUES (77, 'Menciona una materia de la escuela');
INSERT INTO answer (question_id, text, points, position) VALUES
    (77, 'Matemáticas', 36, 1),
    (77, 'Español', 20, 2),
    (77, 'Historia', 15, 3),
    (77, 'Ciencias', 12, 4),
    (77, 'Educación física', 9, 5),
    (77, 'Inglés', 6, 6);

INSERT INTO question (id, text) VALUES (78, 'Menciona algo que hacen los mexicanos durante el Mundial');
INSERT INTO answer (question_id, text, points, position) VALUES
    (78, 'Ver los partidos', 30, 1),
    (78, 'Ponerse la playera', 20, 2),
    (78, 'Gritar «¡Sí se puede!»', 15, 3),
    (78, 'Faltar al trabajo', 13, 4),
    (78, 'Hacer carne asada', 9, 5),
    (78, 'Festejar en el Ángel', 7, 6);

INSERT INTO question (id, text) VALUES (79, 'Menciona un futbolista famoso');
INSERT INTO answer (question_id, text, points, position) VALUES
    (79, 'Messi', 30, 1),
    (79, 'Cristiano Ronaldo', 26, 2),
    (79, 'Hugo Sánchez', 12, 3),
    (79, 'Pelé', 11, 4),
    (79, 'Chicharito', 9, 5),
    (79, 'Maradona', 7, 6);

INSERT INTO question (id, text) VALUES (80, 'Menciona un deporte olímpico');
INSERT INTO answer (question_id, text, points, position) VALUES
    (80, 'Atletismo', 26, 1),
    (80, 'Natación', 24, 2),
    (80, 'Clavados', 15, 3),
    (80, 'Gimnasia', 13, 4),
    (80, 'Box', 10, 5),
    (80, 'Fútbol', 7, 6);

INSERT INTO question (id, text) VALUES (81, 'Menciona un boxeador famoso');
INSERT INTO answer (question_id, text, points, position) VALUES
    (81, 'Julio César Chávez', 32, 1),
    (81, 'Canelo Álvarez', 28, 2),
    (81, 'Muhammad Ali', 14, 3),
    (81, 'Mike Tyson', 12, 4),
    (81, 'Floyd Mayweather', 6, 5),
    (81, 'Manny Pacquiao', 4, 6);

INSERT INTO question (id, text) VALUES (82, 'Menciona un juego de la infancia que se jugaba en la calle');
INSERT INTO answer (question_id, text, points, position) VALUES
    (82, 'Escondidillas', 28, 1),
    (82, 'Las traes', 22, 2),
    (82, 'Avión / bebeleche', 14, 3),
    (82, 'Canicas', 12, 4),
    (82, 'Saltar la cuerda', 10, 5),
    (82, 'Trompo', 7, 6);

INSERT INTO question (id, text) VALUES (83, 'Menciona un juguete clásico');
INSERT INTO answer (question_id, text, points, position) VALUES
    (83, 'Muñeca', 26, 1),
    (83, 'Carritos', 22, 2),
    (83, 'Pelota', 16, 3),
    (83, 'Trompo', 12, 4),
    (83, 'Yoyo', 10, 5),
    (83, 'Balero', 7, 6);

INSERT INTO question (id, text) VALUES (84, 'Menciona un personaje de Toy Story');
INSERT INTO answer (question_id, text, points, position) VALUES
    (84, 'Woody', 34, 1),
    (84, 'Buzz Lightyear', 30, 2),
    (84, 'Jessie', 12, 3),
    (84, 'Rex', 8, 4),
    (84, 'Señor Cara de Papa', 7, 5),
    (84, 'Slinky', 4, 6);

INSERT INTO question (id, text) VALUES (85, 'Menciona algo que se ve en un circo');
INSERT INTO answer (question_id, text, points, position) VALUES
    (85, 'Payasos', 34, 1),
    (85, 'Trapecistas', 18, 2),
    (85, 'Malabaristas', 14, 3),
    (85, 'Leones', 12, 4),
    (85, 'Elefantes', 9, 5),
    (85, 'Mago', 6, 6);

INSERT INTO question (id, text) VALUES (86, 'Menciona un instrumento musical');
INSERT INTO answer (question_id, text, points, position) VALUES
    (86, 'Guitarra', 34, 1),
    (86, 'Piano', 22, 2),
    (86, 'Batería', 14, 3),
    (86, 'Violín', 12, 4),
    (86, 'Trompeta', 9, 5),
    (86, 'Flauta', 5, 6);

INSERT INTO question (id, text) VALUES (87, 'Menciona algo que hay en una boda');
INSERT INTO answer (question_id, text, points, position) VALUES
    (87, 'Pastel', 26, 1),
    (87, 'Vals', 20, 2),
    (87, 'Novios', 16, 3),
    (87, 'Baile', 14, 4),
    (87, 'Ramo', 10, 5),
    (87, 'Barra libre', 8, 6);

INSERT INTO question (id, text) VALUES (88, 'Menciona algo que se regala el Día de las Madres');
INSERT INTO answer (question_id, text, points, position) VALUES
    (88, 'Flores', 34, 1),
    (88, 'Comida en restaurante', 18, 2),
    (88, 'Ropa', 14, 3),
    (88, 'Electrodoméstico', 12, 4),
    (88, 'Joyería', 9, 5),
    (88, 'Serenata', 6, 6);

INSERT INTO question (id, text) VALUES (89, 'Menciona algo que hacen las parejas el 14 de febrero');
INSERT INTO answer (question_id, text, points, position) VALUES
    (89, 'Regalar flores', 30, 1),
    (89, 'Ir a cenar', 24, 2),
    (89, 'Regalar chocolates', 18, 3),
    (89, 'Ir al cine', 10, 4),
    (89, 'Dar una tarjeta', 8, 5),
    (89, 'Regalar un peluche', 6, 6);

INSERT INTO question (id, text) VALUES (90, 'Menciona un disfraz de Halloween');
INSERT INTO answer (question_id, text, points, position) VALUES
    (90, 'Bruja', 26, 1),
    (90, 'Vampiro', 20, 2),
    (90, 'Zombi', 15, 3),
    (90, 'Calabaza', 13, 4),
    (90, 'Fantasma', 10, 5),
    (90, 'Superhéroe', 8, 6);

INSERT INTO question (id, text) VALUES (91, 'Menciona algo que hace la gente en un concierto');
INSERT INTO answer (question_id, text, points, position) VALUES
    (91, 'Cantar', 32, 1),
    (91, 'Bailar', 22, 2),
    (91, 'Grabar con el celular', 18, 3),
    (91, 'Gritar', 12, 4),
    (91, 'Tomar cerveza', 8, 5),
    (91, 'Prender la lámpara del celular', 5, 6);

INSERT INTO question (id, text) VALUES (92, 'Menciona una plataforma para ver películas o series');
INSERT INTO answer (question_id, text, points, position) VALUES
    (92, 'Netflix', 44, 1),
    (92, 'Disney+', 16, 2),
    (92, 'YouTube', 14, 3),
    (92, 'Amazon Prime Video', 12, 4),
    (92, 'Max / HBO', 8, 5),
    (92, 'Vix', 4, 6);

INSERT INTO question (id, text) VALUES (93, 'Menciona una serie famosa');
INSERT INTO answer (question_id, text, points, position) VALUES
    (93, 'Friends', 22, 1),
    (93, 'La casa de papel', 20, 2),
    (93, 'Stranger Things', 17, 3),
    (93, 'Breaking Bad', 14, 4),
    (93, 'Juego de tronos', 12, 5),
    (93, 'Los Simpson', 7, 6);

INSERT INTO question (id, text) VALUES (94, 'Menciona un anime famoso');
INSERT INTO answer (question_id, text, points, position) VALUES
    (94, 'Dragon Ball', 34, 1),
    (94, 'Naruto', 20, 2),
    (94, 'Pokémon', 18, 3),
    (94, 'One Piece', 12, 4),
    (94, 'Los Caballeros del Zodiaco', 9, 5),
    (94, 'Sailor Moon', 5, 6);

INSERT INTO question (id, text) VALUES (95, 'Menciona un Pokémon');
INSERT INTO answer (question_id, text, points, position) VALUES
    (95, 'Pikachu', 44, 1),
    (95, 'Charmander', 14, 2),
    (95, 'Bulbasaur', 11, 3),
    (95, 'Squirtle', 10, 4),
    (95, 'Mewtwo', 8, 5),
    (95, 'Eevee', 6, 6);

INSERT INTO question (id, text) VALUES (96, 'Menciona algo que te da miedo');
INSERT INTO answer (question_id, text, points, position) VALUES
    (96, 'Las arañas', 26, 1),
    (96, 'La oscuridad', 20, 2),
    (96, 'Los fantasmas', 16, 3),
    (96, 'Las alturas', 14, 4),
    (96, 'Las serpientes', 10, 5),
    (96, 'Las cucarachas', 8, 6);

INSERT INTO question (id, text) VALUES (97, 'Menciona una leyenda mexicana');
INSERT INTO answer (question_id, text, points, position) VALUES
    (97, 'La Llorona', 44, 1),
    (97, 'El Chupacabras', 16, 2),
    (97, 'El Charro Negro', 12, 3),
    (97, 'La Nahuala', 10, 4),
    (97, 'El Callejón del Beso', 8, 5),
    (97, 'Los aluxes', 4, 6);

INSERT INTO question (id, text) VALUES (98, 'Menciona algo que compras cada semana en el súper');
INSERT INTO answer (question_id, text, points, position) VALUES
    (98, 'Leche', 28, 1),
    (98, 'Huevo', 22, 2),
    (98, 'Pan', 14, 3),
    (98, 'Fruta y verdura', 13, 4),
    (98, 'Carne', 10, 5),
    (98, 'Papel de baño', 7, 6);

INSERT INTO question (id, text) VALUES (99, 'Menciona algo que se hace en un cumpleaños');
INSERT INTO answer (question_id, text, points, position) VALUES
    (99, 'Partir el pastel', 30, 1),
    (99, 'Cantar Las mañanitas', 26, 2),
    (99, 'Romper la piñata', 15, 3),
    (99, 'Abrir los regalos', 12, 4),
    (99, 'Soplar las velas', 9, 5),
    (99, 'Dar la mordida', 5, 6);

INSERT INTO question (id, text) VALUES (100, 'Menciona una frase que dice todo mexicano');
INSERT INTO answer (question_id, text, points, position) VALUES
    (100, '¿Qué onda?', 26, 1),
    (100, 'No manches', 22, 2),
    (100, 'Ahorita', 18, 3),
    (100, '¿Mande?', 14, 4),
    (100, 'Al rato', 9, 5),
    (100, 'Órale', 7, 6);
