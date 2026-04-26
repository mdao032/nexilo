-- =============================================================================
-- V7 — Tables d'extraction de données structurées
-- Auteur  : Nexilo
-- Date    : 2026-04-25
-- Desc    : extraction_templates (templates prédéfinis) et
--           extraction_results (résultats par document)
-- =============================================================================

-- -----------------------------------------------------------------------
-- Table : extraction_templates
-- Stocke les templates d'extraction (ex : INVOICE, CONTRACT, CV, MEDICAL)
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS extraction_templates (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL UNIQUE,
    description  TEXT,
    fields       JSONB        NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------
-- Table : extraction_results
-- Stocke les résultats d'extraction pour chaque document
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS extraction_results (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID        NOT NULL
                     CONSTRAINT fk_extraction_document
                     REFERENCES summary_documents(id) ON DELETE CASCADE,
    template_id  UUID
                     CONSTRAINT fk_extraction_template
                     REFERENCES extraction_templates(id) ON DELETE SET NULL,
    fields_used  JSONB       NOT NULL,
    result       JSONB       NOT NULL,
    raw_json     TEXT,
    confidence   FLOAT,
    model        VARCHAR(100),
    tokens_used  INTEGER,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_extraction_results_document_id
    ON extraction_results (document_id);
CREATE INDEX IF NOT EXISTS idx_extraction_results_template_id
    ON extraction_results (template_id);

-- -----------------------------------------------------------------------
-- Seeds : 4 templates prédéfinis
-- -----------------------------------------------------------------------
INSERT INTO extraction_templates (id, name, description, fields) VALUES

-- INVOICE
(gen_random_uuid(), 'INVOICE', 'Extraction de données de facture',
 '[
   {"name":"numero",       "description":"Numéro de la facture",        "type":"STRING",  "required":true},
   {"name":"date",         "description":"Date de la facture",          "type":"DATE",    "required":true},
   {"name":"montant_ht",   "description":"Montant hors taxes",          "type":"NUMBER",  "required":true},
   {"name":"tva",          "description":"Montant ou taux de TVA",      "type":"NUMBER",  "required":false},
   {"name":"montant_ttc",  "description":"Montant toutes taxes comprises","type":"NUMBER", "required":true},
   {"name":"vendeur",      "description":"Nom ou raison sociale du vendeur","type":"STRING","required":true},
   {"name":"client",       "description":"Nom ou raison sociale du client","type":"STRING","required":true}
 ]'::jsonb),

-- CONTRACT
(gen_random_uuid(), 'CONTRACT', 'Extraction de données de contrat',
 '[
   {"name":"parties",        "description":"Parties signataires du contrat",          "type":"LIST",   "required":true},
   {"name":"date_debut",     "description":"Date de début ou de signature",           "type":"DATE",   "required":true},
   {"name":"date_fin",       "description":"Date de fin ou d''échéance",              "type":"DATE",   "required":false},
   {"name":"objet",          "description":"Objet ou intitulé du contrat",            "type":"STRING", "required":true},
   {"name":"valeur_contrat", "description":"Valeur financière du contrat si présente","type":"NUMBER", "required":false}
 ]'::jsonb),

-- CV_RESUME
(gen_random_uuid(), 'CV_RESUME', 'Extraction de données d''un CV ou résumé professionnel',
 '[
   {"name":"nom",          "description":"Nom complet du candidat",                "type":"STRING", "required":true},
   {"name":"email",        "description":"Adresse e-mail",                         "type":"STRING", "required":false},
   {"name":"telephone",    "description":"Numéro de téléphone",                    "type":"STRING", "required":false},
   {"name":"competences",  "description":"Liste des compétences techniques",       "type":"LIST",   "required":false},
   {"name":"experiences",  "description":"Liste des expériences professionnelles", "type":"LIST",   "required":false}
 ]'::jsonb),

-- MEDICAL
(gen_random_uuid(), 'MEDICAL', 'Extraction de données d''un document médical',
 '[
   {"name":"patient",      "description":"Nom du patient",                         "type":"STRING", "required":true},
   {"name":"date",         "description":"Date de la consultation ou du document", "type":"DATE",   "required":true},
   {"name":"diagnostic",   "description":"Diagnostic principal",                   "type":"STRING", "required":false},
   {"name":"medicaments",  "description":"Liste des médicaments prescrits",        "type":"LIST",   "required":false},
   {"name":"medecin",      "description":"Nom du médecin ou praticien",            "type":"STRING", "required":false}
 ]'::jsonb)

ON CONFLICT (name) DO NOTHING;

