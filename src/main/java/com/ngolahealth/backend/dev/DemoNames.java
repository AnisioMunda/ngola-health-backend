package com.ngolahealth.backend.dev;

import java.util.List;

/** Nomes, locais e textos clínicos fictícios para os dados de demonstração. */
final class DemoNames {

  private DemoNames() {}

  static final List<String> FEMALE =
      List.of(
          "Ana",
          "Maria",
          "Esperança",
          "Joana",
          "Teresa",
          "Luzia",
          "Isabel",
          "Conceição",
          "Domingas",
          "Rosa",
          "Filomena",
          "Helena",
          "Lurdes",
          "Beatriz",
          "Celeste",
          "Graça",
          "Madalena",
          "Antónia",
          "Fátima",
          "Rita");

  static final List<String> MALE =
      List.of(
          "João",
          "Manuel",
          "António",
          "Pedro",
          "Paulo",
          "Domingos",
          "Francisco",
          "José",
          "Carlos",
          "Mateus",
          "Afonso",
          "Eduardo",
          "Fernando",
          "Agostinho",
          "Lourenço",
          "Nelson",
          "Rui",
          "Sebastião",
          "Miguel",
          "Bento");

  static final List<String> SURNAMES =
      List.of(
          "da Silva",
          "dos Santos",
          "Fernandes",
          "Neto",
          "Gomes",
          "Domingos",
          "Sebastião",
          "Kiala",
          "Mbala",
          "Cassoma",
          "Tchitula",
          "Lumbo",
          "Nzinga",
          "Kambala",
          "Pascoal",
          "Mateus",
          "Cunha",
          "Lopes",
          "Bento",
          "Manuel");

  static final List<String[]> PLACES =
      List.of(
          new String[] {"Luanda", "Luanda"},
          new String[] {"Luanda", "Viana"},
          new String[] {"Luanda", "Cacuaco"},
          new String[] {"Luanda", "Belas"},
          new String[] {"Luanda", "Cazenga"},
          new String[] {"Bengo", "Dande"},
          new String[] {"Benguela", "Lobito"},
          new String[] {"Huambo", "Huambo"});

  /** Queixa, diagnóstico e prioridade de Manchester típica. */
  static final List<String[]> CASES =
      List.of(
          new String[] {"Febre e calafrios há 3 dias", "Malária não complicada", "YELLOW"},
          new String[] {"Febre alta e vómitos", "Malária grave", "ORANGE"},
          new String[] {"Tosse produtiva e febre", "Pneumonia adquirida na comunidade", "YELLOW"},
          new String[] {"Diarreia aquosa", "Gastroenterite aguda", "GREEN"},
          new String[] {"Dor torácica opressiva", "Síndrome coronária aguda", "RED"},
          new String[] {"Cefaleia e tensão elevada", "Crise hipertensiva", "ORANGE"},
          new String[] {"Dor ao urinar", "Infecção urinária", "GREEN"},
          new String[] {"Dor abdominal no flanco direito", "Apendicite aguda", "ORANGE"},
          new String[] {"Tosse seca e rinorreia", "Infecção respiratória alta", "BLUE"},
          new String[] {"Ferida no pé", "Celulite do membro inferior", "GREEN"});
}
