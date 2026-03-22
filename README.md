# Nexilo App

Nexilo est une application backend développée avec Spring Boot.

## Prérequis

Avant de commencer, assurez-vous d'avoir installé les outils suivants sur votre machine :

-   [Java Development Kit (JDK) 21](https://www.oracle.com/java/technologies/downloads/#java21)
-   [Maven](https://maven.apache.org/download.cgi)
-   [PostgreSQL](https://www.postgresql.org/download/)

## Installation et Configuration

1.  **Cloner le dépôt :**

    ```bash
    git clone <votre-url-de-depot>
    cd nexilo
    ```

2.  **Configuration de la base de données :**

    Assurez-vous qu'une instance PostgreSQL est en cours d'exécution.
    Créez une base de données nommée `nexilo_db`.

    ```sql
    CREATE DATABASE nexilo_db;
    ```

    Par défaut, l'application est configurée pour se connecter avec :
    *   **URL** : `jdbc:postgresql://localhost:5432/nexilo_db`
    *   **Utilisateur** : `postgres`
    *   **Mot de passe** : `postgres`

    Vous pouvez modifier ces paramètres dans le fichier `src/main/resources/application.yml`.

## Lancement de l'application

Pour lancer l'application, utilisez la commande Maven suivante à la racine du projet :

```bash
mvn spring-boot:run
```

L'application démarrera sur le port `8080`.

## Documentation API

L'application utilise **SpringDoc OpenAPI** pour générer la documentation de l'API.
Une fois l'application lancée, vous pouvez accéder à :

*   **Swagger UI** : [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
*   **Spécification OpenAPI (JSON)** : [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

## Technologies Utilisées

*   **Java 21**
*   **Spring Boot 3.4.3**
    *   Spring Web
    *   Spring Data JPA
    *   Spring Security
    *   Spring Validation
*   **PostgreSQL** (Base de données)
*   **Lombok** (Boilerplate code reduction)
*   **MapStruct** (Mapping d'objets)
*   **SpringDoc OpenAPI** (Documentation API)

