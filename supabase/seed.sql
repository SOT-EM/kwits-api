insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
    created_at, updated_at, is_sso_user,
    confirmation_token, recovery_token, email_change,
    email_change_token_new, email_change_token_current
)
values (
    '00000000-0000-0000-0000-000000000000',
    '00000000-0000-4000-8000-000000000001',
    'authenticated', 'authenticated',
    'sotmdevs@admin.com',
    crypt('sotmdevs123!', gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"name":"Sotm Devs","username":"SOTMDevs"}',
    now(), now(), false,
    '', '', '', '', ''
)
on conflict (id) do nothing;

insert into auth.identities (
    id, user_id, identity_data, provider, provider_id,
    last_sign_in_at, created_at, updated_at
)
values (
    gen_random_uuid(),
    '00000000-0000-4000-8000-000000000001',
    format('{"sub":"%s","email":"%s"}', '00000000-0000-4000-8000-000000000001', 'sotmdevs@admin.com')::jsonb,
    'email',
    '00000000-0000-4000-8000-000000000001',
    now(), now(), now()
)
on conflict (provider, provider_id) do nothing;