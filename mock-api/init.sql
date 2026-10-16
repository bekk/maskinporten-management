-- =============================================
-- Schema: Create all tables
-- =============================================

CREATE TABLE IF NOT EXISTS scopes (
    name                            VARCHAR(255) PRIMARY KEY,
    prefix                          VARCHAR(255) NOT NULL,
    subscope                        VARCHAR(255) NOT NULL,
    description                     TEXT NOT NULL,
    long_description                TEXT,
    descriptions                    JSONB,
    active                          BOOLEAN NOT NULL DEFAULT true,
    created                         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    allowed_integration_types       TEXT[] NOT NULL DEFAULT '{}',
    owner_orgno                     VARCHAR(20) NOT NULL,
    token_type                      VARCHAR(50) NOT NULL,
    visibility                      VARCHAR(50) NOT NULL,
    requires_user_consent           BOOLEAN NOT NULL DEFAULT false,
    requires_user_authentication    BOOLEAN NOT NULL DEFAULT false,
    requires_pseudonymous_tokens    BOOLEAN NOT NULL DEFAULT false,
    delegation_source               VARCHAR(255),
    at_max_age                      BIGINT NOT NULL DEFAULT 0,
    authorization_max_lifetime      BIGINT,
    accessible_for_all              BOOLEAN NOT NULL DEFAULT false,
    enforced_aud_for_access_token   VARCHAR(255),
    protected                       BOOLEAN,
    supports_european_businesses    BOOLEAN
);

CREATE TABLE IF NOT EXISTS scope_access (
    scope                           VARCHAR(255) NOT NULL REFERENCES scopes(name) ON DELETE CASCADE ON UPDATE CASCADE,
    owner_orgno                     VARCHAR(20) NOT NULL,
    owner_organization_name         VARCHAR(255),
    consumer_orgno                  VARCHAR(20) NOT NULL,
    consumer_organization_name      VARCHAR(255),
    state                           VARCHAR(20) NOT NULL,
    active                          BOOLEAN NOT NULL DEFAULT true,
    created                         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (scope, consumer_orgno)
);

CREATE TABLE IF NOT EXISTS organizations (
    orgnr                           VARCHAR(20) PRIMARY KEY,
    name                            VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS prefixes (
    prefix                          VARCHAR(255) PRIMARY KEY,
    owner_orgno                     VARCHAR(20) NOT NULL,
    organization_name               VARCHAR(255),
    created                         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    active                          BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS delegationsources (
    id                              VARCHAR(255) PRIMARY KEY,
    name                            VARCHAR(255) NOT NULL,
    created                         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated                    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS integrations (
    id                              VARCHAR(255) PRIMARY KEY,
    description                     TEXT,
    customer_organization_number    VARCHAR(20) NOT NULL,
    supplier_organization_number    VARCHAR(20),
    supplier_integration_id         VARCHAR(255),
    integration_type                VARCHAR(50) NOT NULL,
    active                          BOOLEAN NOT NULL DEFAULT true,
    created                         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated                    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS api_resources (
    customer_organization_number    VARCHAR(20) PRIMARY KEY,
    prefix                          TEXT[] NOT NULL DEFAULT '{}',
    consumer_count                  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS changelog (
    id                              SERIAL PRIMARY KEY,
    scope                           VARCHAR(255),
    created                         TIMESTAMPTZ NOT NULL DEFAULT now(),
    message                         TEXT NOT NULL,
    actor_name                      VARCHAR(255) NOT NULL,
    channel                         VARCHAR(50) NOT NULL,
    diff                            TEXT NOT NULL
);


-- =============================================
-- Seed data: Organizations
-- =============================================

INSERT INTO organizations (orgnr, name) VALUES
    ('971040238', 'Statens kartverk'),
    ('991825827', 'Digitaliseringsdirektoratet'),
    ('971032081', 'Statens vegvesen'),
    ('974761076', 'Skatteetaten'),
    ('999601391', 'Miljodirektoratet'),
    ('971278374', 'Statsbygg'),
    ('958935420', 'Oslo kommune'),
    ('942110464', 'Trondheim kommune'),
    ('964338531', 'Bergen kommune'),
    ('931796003', 'KS Digital AS'),
    ('986252932', 'Direktoratet for forvaltning og okonomistyring');


-- =============================================
-- Seed data: Scopes (24 rows)
-- =============================================

INSERT INTO scopes (name, prefix, subscope, description, long_description, descriptions, active, allowed_integration_types, owner_orgno, token_type, visibility, requires_user_consent, requires_user_authentication, requires_pseudonymous_tokens, delegation_source, at_max_age, authorization_max_lifetime, accessible_for_all, enforced_aud_for_access_token, protected, supports_european_businesses)
VALUES
    ('kartverk:matrikkel.read',              'kartverk', 'matrikkel.read',              'Lesetilgang til matrikkelenheter og adresseinformasjon',              'Brukes for oppslag i matrikkeldata som eiendom, adresse og bygningsreferanser.',                                        '{"description#en":"Read access to cadastre units and address data","long_description#en":"Used for lookup in cadastre data such as property, address and building references."}', true,  '{maskinporten}',                        '971040238', 'SELF_CONTAINED', 'PRIVATE',  false, false, false, 'altinn',                    120,  43200, false, 'https://api.kartverket.no', true,  false),
    ('kartverk:matrikkel.write',             'kartverk', 'matrikkel.write',             'Skrivetilgang til matrikkeloppdateringer',                            'Brukes ved registrering og oppdatering av matrikkeldata fra autoriserte fagsystemer.',                                  '{}', true,  '{maskinporten}',                        '971040238', 'OPAQUE',         'INTERNAL', false, false, false, 'altinn',                    60,   14400, false, 'https://api.kartverket.no', true,  false),
    ('kartverk:grunnbok.read',               'kartverk', 'grunnbok.read',               'Lesetilgang til grunnboksopplysninger',                               'Gir tilgang til lesing av grunnboksdata for oppslag og saksbehandling.',                                                '{}', true,  '{maskinporten}',                        '971040238', 'SELF_CONTAINED', 'PRIVATE',  false, false, false, 'altinn',                    120,  43200, false, 'https://api.kartverket.no', true,  false),
    ('kartverk:adresse.read',                'kartverk', 'adresse.read',                'Lesetilgang til offisielle adresser',                                 'Brukes for kvalitetssikring og oppslag i Kartverkets adresseregister.',                                                 '{}', true,  '{maskinporten,api_klient}',             '971040238', 'SELF_CONTAINED', 'PUBLIC',   false, false, false, NULL,                        300,  86400, true,  'https://api.kartverket.no', false, true),
    ('kartverk:geokoding.read',              'kartverk', 'geokoding.read',              'Geokoding av adresser og stedfesting',                                'Brukes for a oversette adressegrunnlag til koordinater i Kartverkets geokodingstjenester.',                             '{}', true,  '{maskinporten,api_klient}',             '971040238', 'SELF_CONTAINED', 'PUBLIC',   false, false, false, NULL,                        300,  86400, true,  'https://api.kartverket.no', false, true),
    ('kartverk:stedsnavn.read',              'kartverk', 'stedsnavn.read',              'Lesetilgang til stedsnavn og navneobjekter',                          'Brukes for oppslag i Kartverkets stedsnavnregister.',                                                                   '{}', true,  '{maskinporten,api_klient,idporten}',    '971040238', 'SELF_CONTAINED', 'PUBLIC',   false, false, false, NULL,                        300,  86400, true,  'https://api.kartverket.no', false, false),
    ('kartverk:eiendomsinformasjon.read',    'kartverk', 'eiendomsinformasjon.read',    'Samlet lesetilgang til eiendomsinformasjon',                          'Brukes for oppslag som kombinerer matrikkel, adresse og tilhorende metadata.',                                          '{}', true,  '{maskinporten}',                        '971040238', 'SELF_CONTAINED', 'PRIVATE',  false, false, false, 'altinn',                    120,  43200, false, 'https://api.kartverket.no', true,  false),
    ('kartverk:naboliste.read',              'kartverk', 'naboliste.read',              'Lesetilgang til nabovarsel og nabolister',                            'Brukes for generering av nabolister i byggesak og planprosesser.',                                                      '{}', true,  '{maskinporten,idporten}',               '971040238', 'SELF_CONTAINED', 'PRIVATE',  false, false, false, 'altinn',                    120,  43200, false, 'https://api.kartverket.no', false, false),
    ('kartverk:hoydedata.read',              'kartverk', 'hoydedata.read',              'Lesetilgang til hoydedata og terrengmodeller',                        'Brukes for uttrekk og analyse av Kartverkets hoydedata i autoriserte integrasjoner.',                                   '{}', true,  '{maskinporten,api_klient,eformidling}', '971040238', 'SELF_CONTAINED', 'PUBLIC',   false, false, false, NULL,                        300,  86400, true,  'https://api.kartverket.no', false, false),
    ('kartverk:intern.audit.read',           'kartverk', 'intern.audit.read',           'Intern lesetilgang til audit- og hendelseslogger',                    'Brukes internt i Kartverket for sporbarhet, support og revisjon.',                                                      '{}', true,  '{maskinporten}',                        '971040238', 'OPAQUE',         'INTERNAL', false, true,  false, NULL,                        60,   14400, false, 'https://api.kartverket.no', true,  false),
    ('kartverk:planregister.read',           'kartverk', 'planregister.read',           'Lesetilgang til planregister og planmetadata',                        'Brukes for innsyn i planer, planstatus og planidentifikatorer.',                                                        '{}', true,  '{maskinporten,api_klient}',             '971040238', 'SELF_CONTAINED', 'PUBLIC',   false, false, false, NULL,                        300,  86400, true,  'https://api.kartverket.no', false, false),
    ('kartverk:planregister.write',          'kartverk', 'planregister.write',          'Skrivetilgang til planregister',                                      'Brukes ved registrering og oppdatering av planmetadata fra godkjente systemer.',                                        '{}', true,  '{idporten,idporten_saml2}',             '971040238', 'OPAQUE',         'PRIVATE',  true,  true,  false, 'kartverket-autorisasjon',   60,   7200,  false, 'https://api.kartverket.no', false, false),
    ('kartverk:ortofoto.read',               'kartverk', 'ortofoto.read',               'Lesetilgang til ortofoto og flislag',                                 'Brukes for henting av ortofoto og relaterte metadata for kart- og analyseformal.',                                      '{}', true,  '{api_klient,maskinporten}',             '971040238', 'SELF_CONTAINED', 'PUBLIC',   false, false, false, NULL,                        300,  86400, true,  'https://api.kartverket.no', false, true),
    ('kartverk:cache.invalidate',            'kartverk', 'cache.invalidate',            'Intern tilgang til a tvinge cache-invalidering',                      'Brukes internt av Kartverket ved deploy og driftsoperasjoner for a invalidere cachelag.',                               '{}', true,  '{maskinporten,ansattporten}',           '971040238', 'OPAQUE',         'INTERNAL', false, false, false, NULL,                        30,   1800,  false, 'https://api.kartverket.no', true,  false),
    ('kartverk:byggesoknad.read',            'kartverk', 'byggesoknad.read',            'Lesetilgang til byggesoknadsrelatert eiendomsgrunnlag',               'Brukes i byggesaksflyt for a hente grunnlagsdata pa vegne av innlogget bruker.',                                        '{}', true,  '{idporten,idporten_saml2}',             '971040238', 'SELF_CONTAINED', 'PRIVATE',  true,  true,  false, NULL,                        120,  14400, false, 'https://api.kartverket.no', false, false),
    ('kartverk:byggesoknad.write',           'kartverk', 'byggesoknad.write',           'Skrivetilgang til byggesoknadsrelaterte oppdateringer',               'Brukes for a sende status og referanser tilbake til Kartverkets byggesaksintegrasjoner.',                               '{}', true,  '{idporten}',                            '971040238', 'OPAQUE',         'PRIVATE',  true,  true,  true,  'kartverket-autorisasjon',   60,   7200,  false, 'https://api.kartverket.no', false, false),
    ('kartverk:europa.geografi.read',        'kartverk', 'europa.geografi.read',        'Lesetilgang til geografiske referansedata for europeiske aktorer',    'Brukes ved deling av referansedata mot europeiske samarbeidspartnere og virksomheter.',                                 '{}', true,  '{maskinporten,api_klient}',             '971040238', 'SELF_CONTAINED', 'PUBLIC',   false, false, false, NULL,                        300,  86400, true,  'https://api.kartverket.no', false, true),
    ('kartverk:saksflyt.metadata.read',      'kartverk', 'saksflyt.metadata.read',      'Lesetilgang til metadata for saksflyt og dokumentflyt',              'Brukes i eFormidling-relaterte integrasjoner for a hente metadata knyttet til sak og dokumentflyt.',                    '{}', true,  '{eformidling}',                         '971040238', 'SELF_CONTAINED', 'PRIVATE',  false, false, false, NULL,                        120,  43200, false, 'https://api.kartverket.no', false, false),
    ('kartverk:varsling.krr.read',           'kartverk', 'varsling.krr.read',           'Lesetilgang til varslingsgrunnlag via KRR-relaterte prosesser',       'Brukes i integrasjoner som trenger kontakt- og reservasjonsrelatert varslingsgrunnlag.',                                '{}', true,  '{krr}',                                 '971040238', 'SELF_CONTAINED', 'PRIVATE',  false, false, false, NULL,                        120,  43200, false, 'https://api.kartverket.no', false, false),
    ('kartverk:ansattporten.innsyn.read',    'kartverk', 'ansattporten.innsyn.read',    'Intern innsynstilgang for ansatte',                                   'Brukes av interne ansatte ved Kartverket for innsyn i bruker- og sakskontekst.',                                        '{}', true,  '{ansattporten}',                        '971040238', 'OPAQUE',         'INTERNAL', false, true,  false, NULL,                        60,   7200,  false, 'https://api.kartverket.no', true,  false),
    ('kartverk:tinglysing.document.read',    'kartverk', 'tinglysing.document.read',    'Lesetilgang til dokumentreferanser fra tinglysing',                   'Brukes for historiske integrasjoner som henter dokumentreferanser og metadata fra tinglysing.',                          '{}', false, '{maskinporten}',                        '971040238', 'SELF_CONTAINED', 'PRIVATE',  false, false, false, NULL,                        120,  43200, false, 'https://api.kartverket.no', true,  false),
    ('kartverk:sefrak.read',                 'kartverk', 'sefrak.read',                 'Lesetilgang til SEFRAK-registrerte bygg',                             'Brukes for oppslag i kulturminnerelaterte bygningsdata og SEFRAK-registreringer.',                                      '{}', true,  '{maskinporten,api_klient}',             '971040238', 'SELF_CONTAINED', 'PUBLIC',   false, false, false, NULL,                        300,  86400, true,  'https://api.kartverket.no', false, false),
    ('kartverk:tilgangsstyring/demo.read',   'kartverk', 'tilgangsstyring/demo.read',   'Lesetilgang til demo-API-et til Team Tilgangsstyring',               'Navngitt slik Digdirator gjor det, <prefiks>:<produkt>/<navn>, med produktet tilgangsstyring.',                         '{}', true,  '{maskinporten}',                        '971040238', 'SELF_CONTAINED', 'PRIVATE',  false, false, false, NULL,                        120,  43200, false, 'https://api.kartverket.no', true,  false),
    ('kartverk:tilgangsstyring/demo.write',  'kartverk', 'tilgangsstyring/demo.write',  'Skrivetilgang til demo-API-et til Team Tilgangsstyring',             'Navngitt slik Digdirator gjor det, <prefiks>:<produkt>/<navn>, med produktet tilgangsstyring.',                         '{}', true,  '{maskinporten}',                        '971040238', 'OPAQUE',         'PRIVATE',  false, false, false, NULL,                        60,   14400, false, 'https://api.kartverket.no', true,  false);


-- =============================================
-- Seed data: Scope access (25 rows)
-- =============================================

INSERT INTO scope_access (scope, owner_orgno, owner_organization_name, consumer_orgno, consumer_organization_name, state, active, created, last_updated) VALUES
    ('kartverk:matrikkel.read',           '971040238', 'Statens kartverk', '971032081', 'Statens vegvesen',                                    'APPROVED',  true,  '2026-03-01T08:00:00Z', '2026-03-01T08:00:00Z'),
    ('kartverk:matrikkel.read',           '971040238', 'Statens kartverk', '958935420', 'Oslo kommune',                                        'REQUESTED', true,  '2026-03-23T09:15:00Z', '2026-03-23T09:15:00Z'),
    ('kartverk:adresse.read',             '971040238', 'Statens kartverk', '971032081', 'Statens vegvesen',                                    'APPROVED',  true,  '2026-03-01T08:05:00Z', '2026-03-01T08:05:00Z'),
    ('kartverk:adresse.read',             '971040238', 'Statens kartverk', '942110464', 'Trondheim kommune',                                   'APPROVED',  true,  '2026-03-02T09:00:00Z', '2026-03-02T09:00:00Z'),
    ('kartverk:adresse.read',             '971040238', 'Statens kartverk', '964338531', 'Bergen kommune',                                      'DENIED',    true,  '2026-03-06T11:00:00Z', '2026-03-06T11:20:00Z'),
    ('kartverk:geokoding.read',           '971040238', 'Statens kartverk', '991825827', 'Digitaliseringsdirektoratet',                         'APPROVED',  true,  '2026-03-01T08:10:00Z', '2026-03-01T08:10:00Z'),
    ('kartverk:geokoding.read',           '971040238', 'Statens kartverk', '931796003', 'KS Digital AS',                                       'APPROVED',  true,  '2026-03-02T12:00:00Z', '2026-03-02T12:00:00Z'),
    ('kartverk:stedsnavn.read',           '971040238', 'Statens kartverk', '991825827', 'Digitaliseringsdirektoratet',                         'APPROVED',  true,  '2026-03-01T08:15:00Z', '2026-03-01T08:15:00Z'),
    ('kartverk:stedsnavn.read',           '971040238', 'Statens kartverk', '958935420', 'Oslo kommune',                                        'APPROVED',  true,  '2026-03-02T10:00:00Z', '2026-03-02T10:00:00Z'),
    ('kartverk:eiendomsinformasjon.read', '971040238', 'Statens kartverk', '974761076', 'Skatteetaten',                                        'APPROVED',  true,  '2026-03-03T10:30:00Z', '2026-03-03T10:30:00Z'),
    ('kartverk:naboliste.read',           '971040238', 'Statens kartverk', '974761076', 'Skatteetaten',                                        'APPROVED',  true,  '2026-03-03T10:35:00Z', '2026-03-03T10:35:00Z'),
    ('kartverk:hoydedata.read',           '971040238', 'Statens kartverk', '999601391', 'Miljodirektoratet',                                   'APPROVED',  true,  '2026-03-04T07:45:00Z', '2026-03-04T07:45:00Z'),
    ('kartverk:hoydedata.read',           '971040238', 'Statens kartverk', '971278374', 'Statsbygg',                                           'APPROVED',  true,  '2026-03-04T08:00:00Z', '2026-03-04T08:00:00Z'),
    ('kartverk:grunnbok.read',            '971040238', 'Statens kartverk', '991825827', 'Digitaliseringsdirektoratet',                         'APPROVED',  false, '2026-02-28T16:00:00Z', '2026-03-22T11:00:00Z'),
    ('kartverk:planregister.read',        '971040238', 'Statens kartverk', '942110464', 'Trondheim kommune',                                   'APPROVED',  true,  '2026-03-05T08:10:00Z', '2026-03-05T08:10:00Z'),
    ('kartverk:planregister.write',       '971040238', 'Statens kartverk', '958935420', 'Oslo kommune',                                        'REQUESTED', true,  '2026-03-20T13:00:00Z', '2026-03-20T13:00:00Z'),
    ('kartverk:ortofoto.read',            '971040238', 'Statens kartverk', '971278374', 'Statsbygg',                                           'APPROVED',  true,  '2026-03-07T08:20:00Z', '2026-03-07T08:20:00Z'),
    ('kartverk:byggesoknad.read',         '971040238', 'Statens kartverk', '958935420', 'Oslo kommune',                                        'APPROVED',  true,  '2026-03-09T09:20:00Z', '2026-03-09T09:20:00Z'),
    ('kartverk:byggesoknad.write',        '971040238', 'Statens kartverk', '958935420', 'Oslo kommune',                                        'DENIED',    true,  '2026-03-10T09:20:00Z', '2026-03-10T09:45:00Z'),
    ('kartverk:saksflyt.metadata.read',   '971040238', 'Statens kartverk', '986252932', 'Direktoratet for forvaltning og okonomistyring',      'APPROVED',  true,  '2026-03-11T10:10:00Z', '2026-03-11T10:10:00Z'),
    ('kartverk:varsling.krr.read',        '971040238', 'Statens kartverk', '931796003', 'KS Digital AS',                                       'APPROVED',  true,  '2026-03-12T10:10:00Z', '2026-03-12T10:10:00Z'),
    ('kartverk:ansattporten.innsyn.read', '971040238', 'Statens kartverk', '971040238', 'Statens kartverk',                                    'APPROVED',  true,  '2026-03-12T12:00:00Z', '2026-03-12T12:00:00Z'),
    ('kartverk:sefrak.read',                 '971040238', 'Statens kartverk', '964338531', 'Bergen kommune',                                      'APPROVED',  true,  '2026-03-13T08:00:00Z', '2026-03-13T08:00:00Z'),
    ('kartverk:tinglysing.document.read',    '971040238', 'Statens kartverk', '974761076', 'Skatteetaten',                                        'APPROVED',  false, '2025-12-15T08:00:00Z', '2026-03-21T14:00:00Z'),
    ('kartverk:tilgangsstyring/demo.read',   '971040238', 'Statens kartverk', '971032081', 'Statens vegvesen',                                    'APPROVED',  true,  '2026-03-14T08:00:00Z', '2026-03-14T08:00:00Z');


-- =============================================
-- Seed data: Prefixes
-- =============================================

INSERT INTO prefixes (prefix, owner_orgno, organization_name, active, created, last_updated) VALUES
    ('kartverk',        '971040238', 'Statens kartverk', true,  '2025-11-18T10:10:00Z', '2026-03-24T07:00:00Z'),
    ('kartverk-intern', '971040238', 'Statens kartverk', true,  '2026-01-05T08:00:00Z', '2026-03-24T07:00:00Z'),
    ('kartverk-legacy', '971040238', 'Statens kartverk', false, '2025-04-02T08:00:00Z', '2026-03-21T14:00:00Z'),
    ('skatt',           '974761076', NULL,               true,  '2025-12-01T08:00:00Z', '2025-12-01T08:00:00Z');


-- =============================================
-- Seed data: Delegation sources
-- =============================================

INSERT INTO delegationsources (id, name, created, last_updated) VALUES
    ('altinn',                      'Altinn delegering',                '2025-11-18T10:10:00Z', '2026-03-24T07:00:00Z'),
    ('kartverket-autorisasjon',     'Kartverket intern autorisasjon',   '2026-01-10T08:00:00Z', '2026-03-24T07:00:00Z'),
    ('kartverket-prosjektavtale',   'Kartverket prosjektavtale',        '2026-01-15T09:00:00Z', '2026-03-24T07:00:00Z'),
    ('geonorge-samarbeidsavtale',   'GeoNorge samarbeidsavtale',        '2026-01-20T09:00:00Z', '2026-03-24T07:00:00Z'),
    ('kommunal-fullmakt',           'Kommunal fullmakt',                '2026-02-01T10:00:00Z', '2026-03-24T07:00:00Z');


-- =============================================
-- Seed data: Integrations
-- =============================================

INSERT INTO integrations (id, description, customer_organization_number, supplier_organization_number, supplier_integration_id, integration_type, active, created, last_updated) VALUES
    ('matrikkel-oppslag',       'Matrikkel oppslag',              '971032081', '971040238', 'kartverket-matrikkel-api-prod',       'MASKINPORTEN',             true,  '2026-03-20T08:30:00Z', '2026-03-22T09:15:00Z'),
    ('grunnbok-oppslag',        'Grunnbok oppslag',               '974761076', '971040238', 'kartverket-grunnbok-api-prod',        'MASKINPORTEN',             true,  '2026-03-20T08:30:00Z', '2026-03-22T09:15:00Z'),
    ('geonorge-harvester',      'GeoNorge metadataharvester',     '991825827', '971040238', 'kartverket-geonorge-harvester-prod',  'MASKINPORTEN',             true,  '2026-03-20T08:30:00Z', '2026-03-22T09:15:00Z'),
    ('ortofoto-api',            'Ortofoto API-klient',            '971278374', '971040238', 'kartverket-ortofoto-api-prod',        'API_KLIENT',               true,  '2026-02-10T10:00:00Z', '2026-03-24T07:00:00Z'),
    ('planinnsyn-oidc',         'Planinnsyn OIDC',                '958935420', '971040238', 'kartverket-planinnsyn-oidc-prod',     'IDPORTEN_OIDC',            true,  '2026-02-05T12:00:00Z', '2026-03-24T07:00:00Z'),
    ('byggesoknad-onbehalfof',  'Byggesoknad OIDC On-Behalf-Of', '958935420', '971040238', 'kartverket-byggesoknad-obo-prod',     'IDPORTEN_OIDC_ONBEHALFOF', true,  '2026-02-06T09:00:00Z', '2026-03-24T07:00:00Z'),
    ('ansattportal-saml2',      'Ansattportal SAML2',             '971040238', '971040238', 'kartverket-ansattportal-saml2-prod',  'IDPORTEN_SAML2',           true,  '2026-02-07T09:00:00Z', '2026-03-24T07:00:00Z'),
    ('krr-varsling',            'KRR REST varsling',              '931796003', '971040238', 'kartverket-krr-rest-varsling-prod',   'KRR_REST',                 true,  '2026-02-12T10:00:00Z', '2026-03-24T07:00:00Z'),
    ('eformidling-saksflyt',    'eFormidling metadataflyt',       '986252932', '971040238', 'kartverket-eformidling-dpe-prod',     'EFORMIDLING_DPE',          true,  '2026-02-11T10:00:00Z', '2026-03-24T07:00:00Z'),
    ('legacy-tinglysing',       'Legacy tinglysing sync',         '974761076', '971040238', 'kartverket-tinglysing-sync-legacy',   'MASKINPORTEN',             false, '2025-09-12T07:20:00Z', '2026-03-21T14:00:00Z'),
    ('skatt-eiendomsoppslag',   'Eiendomsoppslag for Skatteetaten', '974761076', NULL,        NULL,                                  'MASKINPORTEN',             true,  '2026-01-15T10:00:00Z', '2026-01-20T10:00:00Z'),
    ('digdir-api-klient',       NULL,                             '991825827', NULL,        NULL,                                  'API_KLIENT',               true,  '2026-01-16T10:00:00Z', '2026-01-21T10:00:00Z');


-- =============================================
-- Seed data: API Resources
-- =============================================

INSERT INTO api_resources (customer_organization_number, prefix, consumer_count) VALUES
    ('971040238', '{kartverk,kartverk-intern}', 22),
    ('971032081', '{kartverk}',                 4),
    ('974761076', '{kartverk}',                 3),
    ('991825827', '{kartverk}',                 4),
    ('971278374', '{kartverk}',                 2),
    ('958935420', '{kartverk}',                 3),
    ('931796003', '{kartverk}',                 2);


-- =============================================
-- Seed data: Changelog
-- =============================================

INSERT INTO changelog (id, scope, created, message, actor_name, channel, diff) VALUES
    (1,  'kartverk:matrikkel.read',             '2026-03-19T10:00:00Z', 'Tilgang opprettet for Statens vegvesen',             'Maskinporten administrator', 'ADMIN',    '{"consumer_orgno":"971032081","state":"APPROVED"}'),
    (3,  'kartverk:grunnbok.read',              '2026-03-22T11:00:00Z', 'Tilgang deaktivert for Digitaliseringsdirektoratet', 'Saksbehandler Kartverket',   'ADMIN',    '{"consumer_orgno":"991825827","active":false}'),
    (4,  'kartverk:planregister.write',         '2026-03-20T13:00:00Z', 'Tilgang forespurt av Oslo kommune',                  'Oslo kommune',               'EXTERNAL', '{"consumer_orgno":"958935420","state":"REQUESTED"}'),
    (5,  'kartverk:byggesoknad.write',          '2026-03-10T09:45:00Z', 'Tilgang avslatt for Oslo kommune',                   'Kartverket administrator',   'ADMIN',    '{"consumer_orgno":"958935420","state":"DENIED"}'),
    (8,  'kartverk:tinglysing.document.read',   '2026-03-21T14:00:00Z', 'Scope deaktivert',                                   'Maskinporten administrator', 'ADMIN',    '{"active":false}'),
    (10, 'kartverk:cache.invalidate',           '2026-03-24T07:20:00Z', 'Intern driftstilgang opprettet',                     'Kartverket drift',           'ADMIN',    '{"consumer_orgno":"971040238","state":"APPROVED"}');

-- Reset the changelog sequence to continue after the seeded IDs
SELECT setval('changelog_id_seq', (SELECT MAX(id) FROM changelog));
