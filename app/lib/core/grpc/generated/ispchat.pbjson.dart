// This is a generated file - do not edit.
//
// Generated from ispchat.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports
// ignore_for_file: unused_import

import 'dart:convert' as $convert;
import 'dart:core' as $core;
import 'dart:typed_data' as $typed_data;

@$core.Deprecated('Use messageTypeDescriptor instead')
const MessageType$json = {
  '1': 'MessageType',
  '2': [
    {'1': 'UNKNOWN', '2': 0},
    {'1': 'TEXT', '2': 1},
    {'1': 'RING', '2': 2},
    {'1': 'SDP_OFFER', '2': 3},
    {'1': 'SDP_ANSWER', '2': 4},
    {'1': 'ICE_CANDIDATE', '2': 5},
    {'1': 'CALL_DECLINE', '2': 6},
    {'1': 'AUTH_HANDSHAKE', '2': 7},
    {'1': 'SERVER_QUEUED_ACK', '2': 8},
    {'1': 'DELIVERY_ACK', '2': 9},
    {'1': 'DELIVERY_ERROR', '2': 10},
    {'1': 'IDENTITY_REPLACED', '2': 11},
    {'1': 'REVOCATION_NOTICE', '2': 12},
  ],
};

/// Descriptor for `MessageType`. Decode as a `google.protobuf.EnumDescriptorProto`.
final $typed_data.Uint8List messageTypeDescriptor = $convert.base64Decode(
    'CgtNZXNzYWdlVHlwZRILCgdVTktOT1dOEAASCAoEVEVYVBABEggKBFJJTkcQAhINCglTRFBfT0'
    'ZGRVIQAxIOCgpTRFBfQU5TV0VSEAQSEQoNSUNFX0NBTkRJREFURRAFEhAKDENBTExfREVDTElO'
    'RRAGEhIKDkFVVEhfSEFORFNIQUtFEAcSFQoRU0VSVkVSX1FVRVVFRF9BQ0sQCBIQCgxERUxJVk'
    'VSWV9BQ0sQCRISCg5ERUxJVkVSWV9FUlJPUhAKEhUKEUlERU5USVRZX1JFUExBQ0VEEAsSFQoR'
    'UkVWT0NBVElPTl9OT1RJQ0UQDA==');

@$core.Deprecated('Use deviceRoleDescriptor instead')
const DeviceRole$json = {
  '1': 'DeviceRole',
  '2': [
    {'1': 'MOBILE_ROOT', '2': 0},
    {'1': 'DESKTOP', '2': 1},
    {'1': 'ERP_API', '2': 2},
  ],
};

/// Descriptor for `DeviceRole`. Decode as a `google.protobuf.EnumDescriptorProto`.
final $typed_data.Uint8List deviceRoleDescriptor = $convert.base64Decode(
    'CgpEZXZpY2VSb2xlEg8KC01PQklMRV9ST09UEAASCwoHREVTS1RPUBABEgsKB0VSUF9BUEkQAg'
    '==');

@$core.Deprecated('Use documentTypeDescriptor instead')
const DocumentType$json = {
  '1': 'DocumentType',
  '2': [
    {'1': 'DOC_UNKNOWN', '2': 0},
    {'1': 'CPF', '2': 1},
    {'1': 'CNPJ', '2': 2},
  ],
};

/// Descriptor for `DocumentType`. Decode as a `google.protobuf.EnumDescriptorProto`.
final $typed_data.Uint8List documentTypeDescriptor = $convert.base64Decode(
    'CgxEb2N1bWVudFR5cGUSDwoLRE9DX1VOS05PV04QABIHCgNDUEYQARIICgRDTlBKEAI=');

@$core.Deprecated('Use deliveryStatusDescriptor instead')
const DeliveryStatus$json = {
  '1': 'DeliveryStatus',
  '2': [
    {'1': 'DELIVERY_UNKNOWN', '2': 0},
    {'1': 'LIVE_DELIVERED', '2': 1},
    {'1': 'HOME_QUEUED', '2': 2},
    {'1': 'REDIRECT_REQUIRED', '2': 3},
    {'1': 'REJECTED', '2': 4},
  ],
};

/// Descriptor for `DeliveryStatus`. Decode as a `google.protobuf.EnumDescriptorProto`.
final $typed_data.Uint8List deliveryStatusDescriptor = $convert.base64Decode(
    'Cg5EZWxpdmVyeVN0YXR1cxIUChBERUxJVkVSWV9VTktOT1dOEAASEgoOTElWRV9ERUxJVkVSRU'
    'QQARIPCgtIT01FX1FVRVVFRBACEhUKEVJFRElSRUNUX1JFUVVJUkVEEAMSDAoIUkVKRUNURUQQ'
    'BA==');

@$core.Deprecated('Use principalSigningCertDescriptor instead')
const PrincipalSigningCert$json = {
  '1': 'PrincipalSigningCert',
  '2': [
    {'1': 'cert_id', '3': 1, '4': 1, '5': 9, '10': 'certId'},
    {
      '1': 'principal_ed25519_pub',
      '3': 2,
      '4': 1,
      '5': 12,
      '10': 'principalEd25519Pub'
    },
    {
      '1': 'escrow_x25519_pub',
      '3': 3,
      '4': 1,
      '5': 12,
      '10': 'escrowX25519Pub'
    },
    {'1': 'issued_at_ms', '3': 4, '4': 1, '5': 3, '10': 'issuedAtMs'},
    {'1': 'expires_at_ms', '3': 5, '4': 1, '5': 3, '10': 'expiresAtMs'},
    {'1': 'root_signature', '3': 6, '4': 1, '5': 12, '10': 'rootSignature'},
  ],
};

/// Descriptor for `PrincipalSigningCert`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List principalSigningCertDescriptor = $convert.base64Decode(
    'ChRQcmluY2lwYWxTaWduaW5nQ2VydBIXCgdjZXJ0X2lkGAEgASgJUgZjZXJ0SWQSMgoVcHJpbm'
    'NpcGFsX2VkMjU1MTlfcHViGAIgASgMUhNwcmluY2lwYWxFZDI1NTE5UHViEioKEWVzY3Jvd194'
    'MjU1MTlfcHViGAMgASgMUg9lc2Nyb3dYMjU1MTlQdWISIAoMaXNzdWVkX2F0X21zGAQgASgDUg'
    'ppc3N1ZWRBdE1zEiIKDWV4cGlyZXNfYXRfbXMYBSABKANSC2V4cGlyZXNBdE1zEiUKDnJvb3Rf'
    'c2lnbmF0dXJlGAYgASgMUg1yb290U2lnbmF0dXJl');

@$core.Deprecated('Use verificationCredentialDescriptor instead')
const VerificationCredential$json = {
  '1': 'VerificationCredential',
  '2': [
    {'1': 'credential_id', '3': 1, '4': 1, '5': 9, '10': 'credentialId'},
    {'1': 'user_uuid', '3': 2, '4': 1, '5': 9, '10': 'userUuid'},
    {'1': 'root_ed25519_pub', '3': 3, '4': 1, '5': 12, '10': 'rootEd25519Pub'},
    {'1': 'root_x25519_pub', '3': 4, '4': 1, '5': 12, '10': 'rootX25519Pub'},
    {
      '1': 'doc_type',
      '3': 5,
      '4': 1,
      '5': 14,
      '6': '.br.dev.ispchat.grpc.DocumentType',
      '10': 'docType'
    },
    {'1': 'public_cnpj', '3': 6, '4': 1, '5': 9, '10': 'publicCnpj'},
    {
      '1': 'public_display_name',
      '3': 7,
      '4': 1,
      '5': 9,
      '10': 'publicDisplayName'
    },
    {
      '1': 'verifying_isp_cnpj',
      '3': 8,
      '4': 1,
      '5': 9,
      '10': 'verifyingIspCnpj'
    },
    {
      '1': 'verifying_isp_name',
      '3': 9,
      '4': 1,
      '5': 9,
      '10': 'verifyingIspName'
    },
    {
      '1': 'home_node_address',
      '3': 10,
      '4': 1,
      '5': 9,
      '10': 'homeNodeAddress'
    },
    {'1': 'routing_epoch', '3': 11, '4': 1, '5': 3, '10': 'routingEpoch'},
    {'1': 'issued_at_ms', '3': 12, '4': 1, '5': 3, '10': 'issuedAtMs'},
    {'1': 'expires_at_ms', '3': 13, '4': 1, '5': 3, '10': 'expiresAtMs'},
    {
      '1': 'principal_signature',
      '3': 14,
      '4': 1,
      '5': 12,
      '10': 'principalSignature'
    },
    {
      '1': 'principal_cert',
      '3': 15,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.PrincipalSigningCert',
      '10': 'principalCert'
    },
  ],
};

/// Descriptor for `VerificationCredential`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List verificationCredentialDescriptor = $convert.base64Decode(
    'ChZWZXJpZmljYXRpb25DcmVkZW50aWFsEiMKDWNyZWRlbnRpYWxfaWQYASABKAlSDGNyZWRlbn'
    'RpYWxJZBIbCgl1c2VyX3V1aWQYAiABKAlSCHVzZXJVdWlkEigKEHJvb3RfZWQyNTUxOV9wdWIY'
    'AyABKAxSDnJvb3RFZDI1NTE5UHViEiYKD3Jvb3RfeDI1NTE5X3B1YhgEIAEoDFINcm9vdFgyNT'
    'UxOVB1YhI8Cghkb2NfdHlwZRgFIAEoDjIhLmJyLmRldi5pc3BjaGF0LmdycGMuRG9jdW1lbnRU'
    'eXBlUgdkb2NUeXBlEh8KC3B1YmxpY19jbnBqGAYgASgJUgpwdWJsaWNDbnBqEi4KE3B1YmxpY1'
    '9kaXNwbGF5X25hbWUYByABKAlSEXB1YmxpY0Rpc3BsYXlOYW1lEiwKEnZlcmlmeWluZ19pc3Bf'
    'Y25wahgIIAEoCVIQdmVyaWZ5aW5nSXNwQ25wahIsChJ2ZXJpZnlpbmdfaXNwX25hbWUYCSABKA'
    'lSEHZlcmlmeWluZ0lzcE5hbWUSKgoRaG9tZV9ub2RlX2FkZHJlc3MYCiABKAlSD2hvbWVOb2Rl'
    'QWRkcmVzcxIjCg1yb3V0aW5nX2Vwb2NoGAsgASgDUgxyb3V0aW5nRXBvY2gSIAoMaXNzdWVkX2'
    'F0X21zGAwgASgDUgppc3N1ZWRBdE1zEiIKDWV4cGlyZXNfYXRfbXMYDSABKANSC2V4cGlyZXNB'
    'dE1zEi8KE3ByaW5jaXBhbF9zaWduYXR1cmUYDiABKAxSEnByaW5jaXBhbFNpZ25hdHVyZRJQCg'
    '5wcmluY2lwYWxfY2VydBgPIAEoCzIpLmJyLmRldi5pc3BjaGF0LmdycGMuUHJpbmNpcGFsU2ln'
    'bmluZ0NlcnRSDXByaW5jaXBhbENlcnQ=');

@$core.Deprecated('Use deviceDelegationCertDescriptor instead')
const DeviceDelegationCert$json = {
  '1': 'DeviceDelegationCert',
  '2': [
    {'1': 'cert_id', '3': 1, '4': 1, '5': 9, '10': 'certId'},
    {'1': 'user_uuid', '3': 2, '4': 1, '5': 9, '10': 'userUuid'},
    {'1': 'device_id', '3': 3, '4': 1, '5': 9, '10': 'deviceId'},
    {
      '1': 'role',
      '3': 4,
      '4': 1,
      '5': 14,
      '6': '.br.dev.ispchat.grpc.DeviceRole',
      '10': 'role'
    },
    {
      '1': 'device_ed25519_pub',
      '3': 5,
      '4': 1,
      '5': 12,
      '10': 'deviceEd25519Pub'
    },
    {
      '1': 'device_x25519_pub',
      '3': 6,
      '4': 1,
      '5': 12,
      '10': 'deviceX25519Pub'
    },
    {'1': 'issued_at_ms', '3': 7, '4': 1, '5': 3, '10': 'issuedAtMs'},
    {'1': 'expires_at_ms', '3': 8, '4': 1, '5': 3, '10': 'expiresAtMs'},
    {'1': 'root_signature', '3': 9, '4': 1, '5': 12, '10': 'rootSignature'},
  ],
};

/// Descriptor for `DeviceDelegationCert`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List deviceDelegationCertDescriptor = $convert.base64Decode(
    'ChREZXZpY2VEZWxlZ2F0aW9uQ2VydBIXCgdjZXJ0X2lkGAEgASgJUgZjZXJ0SWQSGwoJdXNlcl'
    '91dWlkGAIgASgJUgh1c2VyVXVpZBIbCglkZXZpY2VfaWQYAyABKAlSCGRldmljZUlkEjMKBHJv'
    'bGUYBCABKA4yHy5ici5kZXYuaXNwY2hhdC5ncnBjLkRldmljZVJvbGVSBHJvbGUSLAoSZGV2aW'
    'NlX2VkMjU1MTlfcHViGAUgASgMUhBkZXZpY2VFZDI1NTE5UHViEioKEWRldmljZV94MjU1MTlf'
    'cHViGAYgASgMUg9kZXZpY2VYMjU1MTlQdWISIAoMaXNzdWVkX2F0X21zGAcgASgDUgppc3N1ZW'
    'RBdE1zEiIKDWV4cGlyZXNfYXRfbXMYCCABKANSC2V4cGlyZXNBdE1zEiUKDnJvb3Rfc2lnbmF0'
    'dXJlGAkgASgMUg1yb290U2lnbmF0dXJl');

@$core.Deprecated('Use homeNodeClaimDescriptor instead')
const HomeNodeClaim$json = {
  '1': 'HomeNodeClaim',
  '2': [
    {'1': 'user_uuid', '3': 1, '4': 1, '5': 9, '10': 'userUuid'},
    {'1': 'home_node_address', '3': 2, '4': 1, '5': 9, '10': 'homeNodeAddress'},
    {'1': 'routing_epoch', '3': 3, '4': 1, '5': 3, '10': 'routingEpoch'},
    {'1': 'issued_at_ms', '3': 4, '4': 1, '5': 3, '10': 'issuedAtMs'},
    {'1': 'claim_signature', '3': 5, '4': 1, '5': 12, '10': 'claimSignature'},
    {
      '1': 'signed_by_principal',
      '3': 6,
      '4': 1,
      '5': 8,
      '10': 'signedByPrincipal'
    },
  ],
};

/// Descriptor for `HomeNodeClaim`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List homeNodeClaimDescriptor = $convert.base64Decode(
    'Cg1Ib21lTm9kZUNsYWltEhsKCXVzZXJfdXVpZBgBIAEoCVIIdXNlclV1aWQSKgoRaG9tZV9ub2'
    'RlX2FkZHJlc3MYAiABKAlSD2hvbWVOb2RlQWRkcmVzcxIjCg1yb3V0aW5nX2Vwb2NoGAMgASgD'
    'Ugxyb3V0aW5nRXBvY2gSIAoMaXNzdWVkX2F0X21zGAQgASgDUgppc3N1ZWRBdE1zEicKD2NsYW'
    'ltX3NpZ25hdHVyZRgFIAEoDFIOY2xhaW1TaWduYXR1cmUSLgoTc2lnbmVkX2J5X3ByaW5jaXBh'
    'bBgGIAEoCFIRc2lnbmVkQnlQcmluY2lwYWw=');

@$core.Deprecated('Use wrappedContentKeyDescriptor instead')
const WrappedContentKey$json = {
  '1': 'WrappedContentKey',
  '2': [
    {'1': 'target_device_id', '3': 1, '4': 1, '5': 9, '10': 'targetDeviceId'},
    {'1': 'wrapped_key_blob', '3': 2, '4': 1, '5': 12, '10': 'wrappedKeyBlob'},
  ],
};

/// Descriptor for `WrappedContentKey`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List wrappedContentKeyDescriptor = $convert.base64Decode(
    'ChFXcmFwcGVkQ29udGVudEtleRIoChB0YXJnZXRfZGV2aWNlX2lkGAEgASgJUg50YXJnZXREZX'
    'ZpY2VJZBIoChB3cmFwcGVkX2tleV9ibG9iGAIgASgMUg53cmFwcGVkS2V5QmxvYg==');

@$core.Deprecated('Use multiDeviceEncryptedPayloadDescriptor instead')
const MultiDeviceEncryptedPayload$json = {
  '1': 'MultiDeviceEncryptedPayload',
  '2': [
    {
      '1': 'ephemeral_x25519_pub',
      '3': 1,
      '4': 1,
      '5': 12,
      '10': 'ephemeralX25519Pub'
    },
    {'1': 'content_nonce', '3': 2, '4': 1, '5': 12, '10': 'contentNonce'},
    {
      '1': 'ciphertext_with_tag',
      '3': 3,
      '4': 1,
      '5': 12,
      '10': 'ciphertextWithTag'
    },
    {
      '1': 'wrapped_keys',
      '3': 4,
      '4': 3,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.WrappedContentKey',
      '10': 'wrappedKeys'
    },
  ],
};

/// Descriptor for `MultiDeviceEncryptedPayload`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List multiDeviceEncryptedPayloadDescriptor = $convert.base64Decode(
    'ChtNdWx0aURldmljZUVuY3J5cHRlZFBheWxvYWQSMAoUZXBoZW1lcmFsX3gyNTUxOV9wdWIYAS'
    'ABKAxSEmVwaGVtZXJhbFgyNTUxOVB1YhIjCg1jb250ZW50X25vbmNlGAIgASgMUgxjb250ZW50'
    'Tm9uY2USLgoTY2lwaGVydGV4dF93aXRoX3RhZxgDIAEoDFIRY2lwaGVydGV4dFdpdGhUYWcSSQ'
    'oMd3JhcHBlZF9rZXlzGAQgAygLMiYuYnIuZGV2LmlzcGNoYXQuZ3JwYy5XcmFwcGVkQ29udGVu'
    'dEtleVILd3JhcHBlZEtleXM=');

@$core.Deprecated('Use identityReplacementNoticeDescriptor instead')
const IdentityReplacementNotice$json = {
  '1': 'IdentityReplacementNotice',
  '2': [
    {'1': 'revocation_id', '3': 1, '4': 1, '5': 9, '10': 'revocationId'},
    {'1': 'old_user_uuid', '3': 2, '4': 1, '5': 9, '10': 'oldUserUuid'},
    {'1': 'new_user_uuid', '3': 3, '4': 1, '5': 9, '10': 'newUserUuid'},
    {
      '1': 'verifying_isp_cnpj',
      '3': 4,
      '4': 1,
      '5': 9,
      '10': 'verifyingIspCnpj'
    },
    {'1': 'replaced_at_ms', '3': 5, '4': 1, '5': 3, '10': 'replacedAtMs'},
    {
      '1': 'principal_signature',
      '3': 6,
      '4': 1,
      '5': 12,
      '10': 'principalSignature'
    },
  ],
};

/// Descriptor for `IdentityReplacementNotice`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List identityReplacementNoticeDescriptor = $convert.base64Decode(
    'ChlJZGVudGl0eVJlcGxhY2VtZW50Tm90aWNlEiMKDXJldm9jYXRpb25faWQYASABKAlSDHJldm'
    '9jYXRpb25JZBIiCg1vbGRfdXNlcl91dWlkGAIgASgJUgtvbGRVc2VyVXVpZBIiCg1uZXdfdXNl'
    'cl91dWlkGAMgASgJUgtuZXdVc2VyVXVpZBIsChJ2ZXJpZnlpbmdfaXNwX2NucGoYBCABKAlSEH'
    'ZlcmlmeWluZ0lzcENucGoSJAoOcmVwbGFjZWRfYXRfbXMYBSABKANSDHJlcGxhY2VkQXRNcxIv'
    'ChNwcmluY2lwYWxfc2lnbmF0dXJlGAYgASgMUhJwcmluY2lwYWxTaWduYXR1cmU=');

@$core.Deprecated('Use revocationEventDescriptor instead')
const RevocationEvent$json = {
  '1': 'RevocationEvent',
  '2': [
    {'1': 'revocation_id', '3': 1, '4': 1, '5': 9, '10': 'revocationId'},
    {'1': 'revocation_seq', '3': 2, '4': 1, '5': 3, '10': 'revocationSeq'},
    {'1': 'target_uuid', '3': 3, '4': 1, '5': 9, '10': 'targetUuid'},
    {'1': 'revoked_device_id', '3': 4, '4': 1, '5': 9, '10': 'revokedDeviceId'},
    {'1': 'reason', '3': 5, '4': 1, '5': 9, '10': 'reason'},
    {'1': 'created_at_ms', '3': 6, '4': 1, '5': 3, '10': 'createdAtMs'},
    {
      '1': 'principal_signature',
      '3': 7,
      '4': 1,
      '5': 12,
      '10': 'principalSignature'
    },
    {
      '1': 'replacement_notice',
      '3': 8,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.IdentityReplacementNotice',
      '10': 'replacementNotice'
    },
  ],
};

/// Descriptor for `RevocationEvent`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List revocationEventDescriptor = $convert.base64Decode(
    'Cg9SZXZvY2F0aW9uRXZlbnQSIwoNcmV2b2NhdGlvbl9pZBgBIAEoCVIMcmV2b2NhdGlvbklkEi'
    'UKDnJldm9jYXRpb25fc2VxGAIgASgDUg1yZXZvY2F0aW9uU2VxEh8KC3RhcmdldF91dWlkGAMg'
    'ASgJUgp0YXJnZXRVdWlkEioKEXJldm9rZWRfZGV2aWNlX2lkGAQgASgJUg9yZXZva2VkRGV2aW'
    'NlSWQSFgoGcmVhc29uGAUgASgJUgZyZWFzb24SIgoNY3JlYXRlZF9hdF9tcxgGIAEoA1ILY3Jl'
    'YXRlZEF0TXMSLwoTcHJpbmNpcGFsX3NpZ25hdHVyZRgHIAEoDFIScHJpbmNpcGFsU2lnbmF0dX'
    'JlEl0KEnJlcGxhY2VtZW50X25vdGljZRgIIAEoCzIuLmJyLmRldi5pc3BjaGF0LmdycGMuSWRl'
    'bnRpdHlSZXBsYWNlbWVudE5vdGljZVIRcmVwbGFjZW1lbnROb3RpY2U=');

@$core.Deprecated('Use collaboratorAuthCertDescriptor instead')
const CollaboratorAuthCert$json = {
  '1': 'CollaboratorAuthCert',
  '2': [
    {'1': 'cert_id', '3': 1, '4': 1, '5': 9, '10': 'certId'},
    {'1': 'isp_cnpj', '3': 2, '4': 1, '5': 9, '10': 'ispCnpj'},
    {'1': 'partner_uuid', '3': 3, '4': 1, '5': 9, '10': 'partnerUuid'},
    {
      '1': 'collaborator_uuid',
      '3': 4,
      '4': 1,
      '5': 9,
      '10': 'collaboratorUuid'
    },
    {
      '1': 'collab_cpf_sha256',
      '3': 5,
      '4': 1,
      '5': 12,
      '10': 'collabCpfSha256'
    },
    {
      '1': 'collab_ed25519_pub',
      '3': 6,
      '4': 1,
      '5': 12,
      '10': 'collabEd25519Pub'
    },
    {'1': 'issued_at_ms', '3': 7, '4': 1, '5': 3, '10': 'issuedAtMs'},
    {'1': 'expires_at_ms', '3': 8, '4': 1, '5': 3, '10': 'expiresAtMs'},
    {
      '1': 'partner_signature',
      '3': 9,
      '4': 1,
      '5': 12,
      '10': 'partnerSignature'
    },
  ],
};

/// Descriptor for `CollaboratorAuthCert`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List collaboratorAuthCertDescriptor = $convert.base64Decode(
    'ChRDb2xsYWJvcmF0b3JBdXRoQ2VydBIXCgdjZXJ0X2lkGAEgASgJUgZjZXJ0SWQSGQoIaXNwX2'
    'NucGoYAiABKAlSB2lzcENucGoSIQoMcGFydG5lcl91dWlkGAMgASgJUgtwYXJ0bmVyVXVpZBIr'
    'ChFjb2xsYWJvcmF0b3JfdXVpZBgEIAEoCVIQY29sbGFib3JhdG9yVXVpZBIqChFjb2xsYWJfY3'
    'BmX3NoYTI1NhgFIAEoDFIPY29sbGFiQ3BmU2hhMjU2EiwKEmNvbGxhYl9lZDI1NTE5X3B1YhgG'
    'IAEoDFIQY29sbGFiRWQyNTUxOVB1YhIgCgxpc3N1ZWRfYXRfbXMYByABKANSCmlzc3VlZEF0TX'
    'MSIgoNZXhwaXJlc19hdF9tcxgIIAEoA1ILZXhwaXJlc0F0TXMSKwoRcGFydG5lcl9zaWduYXR1'
    'cmUYCSABKAxSEHBhcnRuZXJTaWduYXR1cmU=');

@$core.Deprecated('Use registerSessionRequestDescriptor instead')
const RegisterSessionRequest$json = {
  '1': 'RegisterSessionRequest',
  '2': [
    {'1': 'user_uuid', '3': 1, '4': 1, '5': 9, '10': 'userUuid'},
    {'1': 'node_address', '3': 2, '4': 1, '5': 9, '10': 'nodeAddress'},
    {'1': 'public_key', '3': 3, '4': 1, '5': 12, '10': 'publicKey'},
    {
      '1': 'x25519_public_key',
      '3': 4,
      '4': 1,
      '5': 12,
      '10': 'x25519PublicKey'
    },
    {'1': 'signature', '3': 5, '4': 1, '5': 12, '10': 'signature'},
    {'1': 'timestamp', '3': 6, '4': 1, '5': 3, '10': 'timestamp'},
    {'1': 'device_id', '3': 7, '4': 1, '5': 9, '10': 'deviceId'},
    {'1': 'root_ed25519_pub', '3': 8, '4': 1, '5': 12, '10': 'rootEd25519Pub'},
    {
      '1': 'delegation_cert',
      '3': 9,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.DeviceDelegationCert',
      '10': 'delegationCert'
    },
    {
      '1': 'verification_credential',
      '3': 10,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.VerificationCredential',
      '10': 'verificationCredential'
    },
    {
      '1': 'home_node_claim',
      '3': 11,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.HomeNodeClaim',
      '10': 'homeNodeClaim'
    },
  ],
};

/// Descriptor for `RegisterSessionRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List registerSessionRequestDescriptor = $convert.base64Decode(
    'ChZSZWdpc3RlclNlc3Npb25SZXF1ZXN0EhsKCXVzZXJfdXVpZBgBIAEoCVIIdXNlclV1aWQSIQ'
    'oMbm9kZV9hZGRyZXNzGAIgASgJUgtub2RlQWRkcmVzcxIdCgpwdWJsaWNfa2V5GAMgASgMUglw'
    'dWJsaWNLZXkSKgoReDI1NTE5X3B1YmxpY19rZXkYBCABKAxSD3gyNTUxOVB1YmxpY0tleRIcCg'
    'lzaWduYXR1cmUYBSABKAxSCXNpZ25hdHVyZRIcCgl0aW1lc3RhbXAYBiABKANSCXRpbWVzdGFt'
    'cBIbCglkZXZpY2VfaWQYByABKAlSCGRldmljZUlkEigKEHJvb3RfZWQyNTUxOV9wdWIYCCABKA'
    'xSDnJvb3RFZDI1NTE5UHViElIKD2RlbGVnYXRpb25fY2VydBgJIAEoCzIpLmJyLmRldi5pc3Bj'
    'aGF0LmdycGMuRGV2aWNlRGVsZWdhdGlvbkNlcnRSDmRlbGVnYXRpb25DZXJ0EmQKF3ZlcmlmaW'
    'NhdGlvbl9jcmVkZW50aWFsGAogASgLMisuYnIuZGV2LmlzcGNoYXQuZ3JwYy5WZXJpZmljYXRp'
    'b25DcmVkZW50aWFsUhZ2ZXJpZmljYXRpb25DcmVkZW50aWFsEkoKD2hvbWVfbm9kZV9jbGFpbR'
    'gLIAEoCzIiLmJyLmRldi5pc3BjaGF0LmdycGMuSG9tZU5vZGVDbGFpbVINaG9tZU5vZGVDbGFp'
    'bQ==');

@$core.Deprecated('Use registerSessionResponseDescriptor instead')
const RegisterSessionResponse$json = {
  '1': 'RegisterSessionResponse',
  '2': [
    {'1': 'success', '3': 1, '4': 1, '5': 8, '10': 'success'},
    {'1': 'error_message', '3': 2, '4': 1, '5': 9, '10': 'errorMessage'},
  ],
};

/// Descriptor for `RegisterSessionResponse`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List registerSessionResponseDescriptor =
    $convert.base64Decode(
        'ChdSZWdpc3RlclNlc3Npb25SZXNwb25zZRIYCgdzdWNjZXNzGAEgASgIUgdzdWNjZXNzEiMKDW'
        'Vycm9yX21lc3NhZ2UYAiABKAlSDGVycm9yTWVzc2FnZQ==');

@$core.Deprecated('Use findUserSessionsRequestDescriptor instead')
const FindUserSessionsRequest$json = {
  '1': 'FindUserSessionsRequest',
  '2': [
    {'1': 'target_uuid', '3': 1, '4': 1, '5': 9, '10': 'targetUuid'},
  ],
};

/// Descriptor for `FindUserSessionsRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List findUserSessionsRequestDescriptor =
    $convert.base64Decode(
        'ChdGaW5kVXNlclNlc3Npb25zUmVxdWVzdBIfCgt0YXJnZXRfdXVpZBgBIAEoCVIKdGFyZ2V0VX'
        'VpZA==');

@$core.Deprecated('Use userSessionInfoDescriptor instead')
const UserSessionInfo$json = {
  '1': 'UserSessionInfo',
  '2': [
    {'1': 'node_address', '3': 1, '4': 1, '5': 9, '10': 'nodeAddress'},
    {
      '1': 'ed25519_public_key',
      '3': 2,
      '4': 1,
      '5': 12,
      '10': 'ed25519PublicKey'
    },
    {
      '1': 'x25519_public_key',
      '3': 3,
      '4': 1,
      '5': 12,
      '10': 'x25519PublicKey'
    },
    {'1': 'timestamp', '3': 4, '4': 1, '5': 3, '10': 'timestamp'},
    {'1': 'device_id', '3': 5, '4': 1, '5': 9, '10': 'deviceId'},
    {'1': 'root_ed25519_pub', '3': 6, '4': 1, '5': 12, '10': 'rootEd25519Pub'},
    {
      '1': 'device_ed25519_pub',
      '3': 7,
      '4': 1,
      '5': 12,
      '10': 'deviceEd25519Pub'
    },
    {
      '1': 'session_signature',
      '3': 8,
      '4': 1,
      '5': 12,
      '10': 'sessionSignature'
    },
    {
      '1': 'delegation_cert',
      '3': 9,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.DeviceDelegationCert',
      '10': 'delegationCert'
    },
    {
      '1': 'verification_credential',
      '3': 10,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.VerificationCredential',
      '10': 'verificationCredential'
    },
  ],
};

/// Descriptor for `UserSessionInfo`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List userSessionInfoDescriptor = $convert.base64Decode(
    'Cg9Vc2VyU2Vzc2lvbkluZm8SIQoMbm9kZV9hZGRyZXNzGAEgASgJUgtub2RlQWRkcmVzcxIsCh'
    'JlZDI1NTE5X3B1YmxpY19rZXkYAiABKAxSEGVkMjU1MTlQdWJsaWNLZXkSKgoReDI1NTE5X3B1'
    'YmxpY19rZXkYAyABKAxSD3gyNTUxOVB1YmxpY0tleRIcCgl0aW1lc3RhbXAYBCABKANSCXRpbW'
    'VzdGFtcBIbCglkZXZpY2VfaWQYBSABKAlSCGRldmljZUlkEigKEHJvb3RfZWQyNTUxOV9wdWIY'
    'BiABKAxSDnJvb3RFZDI1NTE5UHViEiwKEmRldmljZV9lZDI1NTE5X3B1YhgHIAEoDFIQZGV2aW'
    'NlRWQyNTUxOVB1YhIrChFzZXNzaW9uX3NpZ25hdHVyZRgIIAEoDFIQc2Vzc2lvblNpZ25hdHVy'
    'ZRJSCg9kZWxlZ2F0aW9uX2NlcnQYCSABKAsyKS5ici5kZXYuaXNwY2hhdC5ncnBjLkRldmljZU'
    'RlbGVnYXRpb25DZXJ0Ug5kZWxlZ2F0aW9uQ2VydBJkChd2ZXJpZmljYXRpb25fY3JlZGVudGlh'
    'bBgKIAEoCzIrLmJyLmRldi5pc3BjaGF0LmdycGMuVmVyaWZpY2F0aW9uQ3JlZGVudGlhbFIWdm'
    'VyaWZpY2F0aW9uQ3JlZGVudGlhbA==');

@$core.Deprecated('Use findUserSessionsResponseDescriptor instead')
const FindUserSessionsResponse$json = {
  '1': 'FindUserSessionsResponse',
  '2': [
    {'1': 'node_addresses', '3': 1, '4': 3, '5': 9, '10': 'nodeAddresses'},
    {
      '1': 'sessions',
      '3': 2,
      '4': 3,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.UserSessionInfo',
      '10': 'sessions'
    },
    {
      '1': 'home_node_claim',
      '3': 3,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.HomeNodeClaim',
      '10': 'homeNodeClaim'
    },
    {
      '1': 'replacement_notice',
      '3': 4,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.IdentityReplacementNotice',
      '10': 'replacementNotice'
    },
  ],
};

/// Descriptor for `FindUserSessionsResponse`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List findUserSessionsResponseDescriptor = $convert.base64Decode(
    'ChhGaW5kVXNlclNlc3Npb25zUmVzcG9uc2USJQoObm9kZV9hZGRyZXNzZXMYASADKAlSDW5vZG'
    'VBZGRyZXNzZXMSQAoIc2Vzc2lvbnMYAiADKAsyJC5ici5kZXYuaXNwY2hhdC5ncnBjLlVzZXJT'
    'ZXNzaW9uSW5mb1IIc2Vzc2lvbnMSSgoPaG9tZV9ub2RlX2NsYWltGAMgASgLMiIuYnIuZGV2Lm'
    'lzcGNoYXQuZ3JwYy5Ib21lTm9kZUNsYWltUg1ob21lTm9kZUNsYWltEl0KEnJlcGxhY2VtZW50'
    'X25vdGljZRgEIAEoCzIuLmJyLmRldi5pc3BjaGF0LmdycGMuSWRlbnRpdHlSZXBsYWNlbWVudE'
    '5vdGljZVIRcmVwbGFjZW1lbnROb3RpY2U=');

@$core.Deprecated('Use clientEnvelopeDescriptor instead')
const ClientEnvelope$json = {
  '1': 'ClientEnvelope',
  '2': [
    {'1': 'message_id', '3': 1, '4': 1, '5': 9, '10': 'messageId'},
    {'1': 'sender_uuid', '3': 2, '4': 1, '5': 9, '10': 'senderUuid'},
    {'1': 'receiver_uuid', '3': 3, '4': 1, '5': 9, '10': 'receiverUuid'},
    {
      '1': 'type',
      '3': 4,
      '4': 1,
      '5': 14,
      '6': '.br.dev.ispchat.grpc.MessageType',
      '10': 'type'
    },
    {
      '1': 'encrypted_payload',
      '3': 5,
      '4': 1,
      '5': 12,
      '10': 'encryptedPayload'
    },
    {
      '1': 'sender_public_key',
      '3': 6,
      '4': 1,
      '5': 12,
      '10': 'senderPublicKey'
    },
    {'1': 'signature', '3': 7, '4': 1, '5': 12, '10': 'signature'},
    {'1': 'timestamp', '3': 8, '4': 1, '5': 3, '10': 'timestamp'},
    {'1': 'sender_device_id', '3': 9, '4': 1, '5': 9, '10': 'senderDeviceId'},
    {
      '1': 'sender_delegation_cert',
      '3': 10,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.DeviceDelegationCert',
      '10': 'senderDelegationCert'
    },
    {
      '1': 'sender_verification_credential',
      '3': 11,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.VerificationCredential',
      '10': 'senderVerificationCredential'
    },
    {'1': 'ref_message_id', '3': 12, '4': 1, '5': 9, '10': 'refMessageId'},
  ],
};

/// Descriptor for `ClientEnvelope`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List clientEnvelopeDescriptor = $convert.base64Decode(
    'Cg5DbGllbnRFbnZlbG9wZRIdCgptZXNzYWdlX2lkGAEgASgJUgltZXNzYWdlSWQSHwoLc2VuZG'
    'VyX3V1aWQYAiABKAlSCnNlbmRlclV1aWQSIwoNcmVjZWl2ZXJfdXVpZBgDIAEoCVIMcmVjZWl2'
    'ZXJVdWlkEjQKBHR5cGUYBCABKA4yIC5ici5kZXYuaXNwY2hhdC5ncnBjLk1lc3NhZ2VUeXBlUg'
    'R0eXBlEisKEWVuY3J5cHRlZF9wYXlsb2FkGAUgASgMUhBlbmNyeXB0ZWRQYXlsb2FkEioKEXNl'
    'bmRlcl9wdWJsaWNfa2V5GAYgASgMUg9zZW5kZXJQdWJsaWNLZXkSHAoJc2lnbmF0dXJlGAcgAS'
    'gMUglzaWduYXR1cmUSHAoJdGltZXN0YW1wGAggASgDUgl0aW1lc3RhbXASKAoQc2VuZGVyX2Rl'
    'dmljZV9pZBgJIAEoCVIOc2VuZGVyRGV2aWNlSWQSXwoWc2VuZGVyX2RlbGVnYXRpb25fY2VydB'
    'gKIAEoCzIpLmJyLmRldi5pc3BjaGF0LmdycGMuRGV2aWNlRGVsZWdhdGlvbkNlcnRSFHNlbmRl'
    'ckRlbGVnYXRpb25DZXJ0EnEKHnNlbmRlcl92ZXJpZmljYXRpb25fY3JlZGVudGlhbBgLIAEoCz'
    'IrLmJyLmRldi5pc3BjaGF0LmdycGMuVmVyaWZpY2F0aW9uQ3JlZGVudGlhbFIcc2VuZGVyVmVy'
    'aWZpY2F0aW9uQ3JlZGVudGlhbBIkCg5yZWZfbWVzc2FnZV9pZBgMIAEoCVIMcmVmTWVzc2FnZU'
    'lk');

@$core.Deprecated('Use serverEnvelopeDescriptor instead')
const ServerEnvelope$json = {
  '1': 'ServerEnvelope',
  '2': [
    {'1': 'message_id', '3': 1, '4': 1, '5': 9, '10': 'messageId'},
    {'1': 'sender_uuid', '3': 2, '4': 1, '5': 9, '10': 'senderUuid'},
    {
      '1': 'type',
      '3': 3,
      '4': 1,
      '5': 14,
      '6': '.br.dev.ispchat.grpc.MessageType',
      '10': 'type'
    },
    {
      '1': 'encrypted_payload',
      '3': 4,
      '4': 1,
      '5': 12,
      '10': 'encryptedPayload'
    },
    {
      '1': 'sender_public_key',
      '3': 5,
      '4': 1,
      '5': 12,
      '10': 'senderPublicKey'
    },
    {'1': 'signature', '3': 6, '4': 1, '5': 12, '10': 'signature'},
    {'1': 'timestamp', '3': 7, '4': 1, '5': 3, '10': 'timestamp'},
    {'1': 'receiver_uuid', '3': 8, '4': 1, '5': 9, '10': 'receiverUuid'},
    {'1': 'sender_device_id', '3': 9, '4': 1, '5': 9, '10': 'senderDeviceId'},
    {
      '1': 'sender_delegation_cert',
      '3': 10,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.DeviceDelegationCert',
      '10': 'senderDelegationCert'
    },
    {
      '1': 'sender_verification_credential',
      '3': 11,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.VerificationCredential',
      '10': 'senderVerificationCredential'
    },
    {'1': 'ref_message_id', '3': 12, '4': 1, '5': 9, '10': 'refMessageId'},
    {
      '1': 'replacement_notice',
      '3': 13,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.IdentityReplacementNotice',
      '10': 'replacementNotice'
    },
    {
      '1': 'revocation_event',
      '3': 14,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.RevocationEvent',
      '10': 'revocationEvent'
    },
  ],
};

/// Descriptor for `ServerEnvelope`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List serverEnvelopeDescriptor = $convert.base64Decode(
    'Cg5TZXJ2ZXJFbnZlbG9wZRIdCgptZXNzYWdlX2lkGAEgASgJUgltZXNzYWdlSWQSHwoLc2VuZG'
    'VyX3V1aWQYAiABKAlSCnNlbmRlclV1aWQSNAoEdHlwZRgDIAEoDjIgLmJyLmRldi5pc3BjaGF0'
    'LmdycGMuTWVzc2FnZVR5cGVSBHR5cGUSKwoRZW5jcnlwdGVkX3BheWxvYWQYBCABKAxSEGVuY3'
    'J5cHRlZFBheWxvYWQSKgoRc2VuZGVyX3B1YmxpY19rZXkYBSABKAxSD3NlbmRlclB1YmxpY0tl'
    'eRIcCglzaWduYXR1cmUYBiABKAxSCXNpZ25hdHVyZRIcCgl0aW1lc3RhbXAYByABKANSCXRpbW'
    'VzdGFtcBIjCg1yZWNlaXZlcl91dWlkGAggASgJUgxyZWNlaXZlclV1aWQSKAoQc2VuZGVyX2Rl'
    'dmljZV9pZBgJIAEoCVIOc2VuZGVyRGV2aWNlSWQSXwoWc2VuZGVyX2RlbGVnYXRpb25fY2VydB'
    'gKIAEoCzIpLmJyLmRldi5pc3BjaGF0LmdycGMuRGV2aWNlRGVsZWdhdGlvbkNlcnRSFHNlbmRl'
    'ckRlbGVnYXRpb25DZXJ0EnEKHnNlbmRlcl92ZXJpZmljYXRpb25fY3JlZGVudGlhbBgLIAEoCz'
    'IrLmJyLmRldi5pc3BjaGF0LmdycGMuVmVyaWZpY2F0aW9uQ3JlZGVudGlhbFIcc2VuZGVyVmVy'
    'aWZpY2F0aW9uQ3JlZGVudGlhbBIkCg5yZWZfbWVzc2FnZV9pZBgMIAEoCVIMcmVmTWVzc2FnZU'
    'lkEl0KEnJlcGxhY2VtZW50X25vdGljZRgNIAEoCzIuLmJyLmRldi5pc3BjaGF0LmdycGMuSWRl'
    'bnRpdHlSZXBsYWNlbWVudE5vdGljZVIRcmVwbGFjZW1lbnROb3RpY2USTwoQcmV2b2NhdGlvbl'
    '9ldmVudBgOIAEoCzIkLmJyLmRldi5pc3BjaGF0LmdycGMuUmV2b2NhdGlvbkV2ZW50Ug9yZXZv'
    'Y2F0aW9uRXZlbnQ=');

@$core.Deprecated('Use pingRequestDescriptor instead')
const PingRequest$json = {
  '1': 'PingRequest',
  '2': [
    {'1': 'sender_node_id', '3': 1, '4': 1, '5': 9, '10': 'senderNodeId'},
    {'1': 'sender_address', '3': 2, '4': 1, '5': 9, '10': 'senderAddress'},
  ],
};

/// Descriptor for `PingRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List pingRequestDescriptor = $convert.base64Decode(
    'CgtQaW5nUmVxdWVzdBIkCg5zZW5kZXJfbm9kZV9pZBgBIAEoCVIMc2VuZGVyTm9kZUlkEiUKDn'
    'NlbmRlcl9hZGRyZXNzGAIgASgJUg1zZW5kZXJBZGRyZXNz');

@$core.Deprecated('Use pingResponseDescriptor instead')
const PingResponse$json = {
  '1': 'PingResponse',
  '2': [
    {'1': 'responder_node_id', '3': 1, '4': 1, '5': 9, '10': 'responderNodeId'},
  ],
};

/// Descriptor for `PingResponse`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List pingResponseDescriptor = $convert.base64Decode(
    'CgxQaW5nUmVzcG9uc2USKgoRcmVzcG9uZGVyX25vZGVfaWQYASABKAlSD3Jlc3BvbmRlck5vZG'
    'VJZA==');

@$core.Deprecated('Use storeRequestDescriptor instead')
const StoreRequest$json = {
  '1': 'StoreRequest',
  '2': [
    {'1': 'user_uuid', '3': 1, '4': 1, '5': 9, '10': 'userUuid'},
    {'1': 'node_address', '3': 2, '4': 1, '5': 9, '10': 'nodeAddress'},
    {'1': 'public_key', '3': 3, '4': 1, '5': 12, '10': 'publicKey'},
    {
      '1': 'x25519_public_key',
      '3': 4,
      '4': 1,
      '5': 12,
      '10': 'x25519PublicKey'
    },
    {'1': 'signature', '3': 5, '4': 1, '5': 12, '10': 'signature'},
    {'1': 'timestamp', '3': 6, '4': 1, '5': 3, '10': 'timestamp'},
    {'1': 'device_id', '3': 7, '4': 1, '5': 9, '10': 'deviceId'},
    {'1': 'root_ed25519_pub', '3': 8, '4': 1, '5': 12, '10': 'rootEd25519Pub'},
    {
      '1': 'delegation_cert',
      '3': 9,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.DeviceDelegationCert',
      '10': 'delegationCert'
    },
    {
      '1': 'verification_credential',
      '3': 10,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.VerificationCredential',
      '10': 'verificationCredential'
    },
    {
      '1': 'home_node_claim',
      '3': 11,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.HomeNodeClaim',
      '10': 'homeNodeClaim'
    },
  ],
};

/// Descriptor for `StoreRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List storeRequestDescriptor = $convert.base64Decode(
    'CgxTdG9yZVJlcXVlc3QSGwoJdXNlcl91dWlkGAEgASgJUgh1c2VyVXVpZBIhCgxub2RlX2FkZH'
    'Jlc3MYAiABKAlSC25vZGVBZGRyZXNzEh0KCnB1YmxpY19rZXkYAyABKAxSCXB1YmxpY0tleRIq'
    'ChF4MjU1MTlfcHVibGljX2tleRgEIAEoDFIPeDI1NTE5UHVibGljS2V5EhwKCXNpZ25hdHVyZR'
    'gFIAEoDFIJc2lnbmF0dXJlEhwKCXRpbWVzdGFtcBgGIAEoA1IJdGltZXN0YW1wEhsKCWRldmlj'
    'ZV9pZBgHIAEoCVIIZGV2aWNlSWQSKAoQcm9vdF9lZDI1NTE5X3B1YhgIIAEoDFIOcm9vdEVkMj'
    'U1MTlQdWISUgoPZGVsZWdhdGlvbl9jZXJ0GAkgASgLMikuYnIuZGV2LmlzcGNoYXQuZ3JwYy5E'
    'ZXZpY2VEZWxlZ2F0aW9uQ2VydFIOZGVsZWdhdGlvbkNlcnQSZAoXdmVyaWZpY2F0aW9uX2NyZW'
    'RlbnRpYWwYCiABKAsyKy5ici5kZXYuaXNwY2hhdC5ncnBjLlZlcmlmaWNhdGlvbkNyZWRlbnRp'
    'YWxSFnZlcmlmaWNhdGlvbkNyZWRlbnRpYWwSSgoPaG9tZV9ub2RlX2NsYWltGAsgASgLMiIuYn'
    'IuZGV2LmlzcGNoYXQuZ3JwYy5Ib21lTm9kZUNsYWltUg1ob21lTm9kZUNsYWlt');

@$core.Deprecated('Use storeResponseDescriptor instead')
const StoreResponse$json = {
  '1': 'StoreResponse',
  '2': [
    {'1': 'success', '3': 1, '4': 1, '5': 8, '10': 'success'},
  ],
};

/// Descriptor for `StoreResponse`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List storeResponseDescriptor = $convert
    .base64Decode('Cg1TdG9yZVJlc3BvbnNlEhgKB3N1Y2Nlc3MYASABKAhSB3N1Y2Nlc3M=');

@$core.Deprecated('Use nodeInfoDescriptor instead')
const NodeInfo$json = {
  '1': 'NodeInfo',
  '2': [
    {'1': 'node_id', '3': 1, '4': 1, '5': 9, '10': 'nodeId'},
    {'1': 'node_address', '3': 2, '4': 1, '5': 9, '10': 'nodeAddress'},
  ],
};

/// Descriptor for `NodeInfo`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List nodeInfoDescriptor = $convert.base64Decode(
    'CghOb2RlSW5mbxIXCgdub2RlX2lkGAEgASgJUgZub2RlSWQSIQoMbm9kZV9hZGRyZXNzGAIgAS'
    'gJUgtub2RlQWRkcmVzcw==');

@$core.Deprecated('Use findNodeRequestDescriptor instead')
const FindNodeRequest$json = {
  '1': 'FindNodeRequest',
  '2': [
    {'1': 'target_id', '3': 1, '4': 1, '5': 9, '10': 'targetId'},
  ],
};

/// Descriptor for `FindNodeRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List findNodeRequestDescriptor = $convert.base64Decode(
    'Cg9GaW5kTm9kZVJlcXVlc3QSGwoJdGFyZ2V0X2lkGAEgASgJUgh0YXJnZXRJZA==');

@$core.Deprecated('Use findNodeResponseDescriptor instead')
const FindNodeResponse$json = {
  '1': 'FindNodeResponse',
  '2': [
    {
      '1': 'closest_nodes',
      '3': 1,
      '4': 3,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.NodeInfo',
      '10': 'closestNodes'
    },
  ],
};

/// Descriptor for `FindNodeResponse`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List findNodeResponseDescriptor = $convert.base64Decode(
    'ChBGaW5kTm9kZVJlc3BvbnNlEkIKDWNsb3Nlc3Rfbm9kZXMYASADKAsyHS5ici5kZXYuaXNwY2'
    'hhdC5ncnBjLk5vZGVJbmZvUgxjbG9zZXN0Tm9kZXM=');

@$core.Deprecated('Use findValueRequestDescriptor instead')
const FindValueRequest$json = {
  '1': 'FindValueRequest',
  '2': [
    {'1': 'target_uuid', '3': 1, '4': 1, '5': 9, '10': 'targetUuid'},
  ],
};

/// Descriptor for `FindValueRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List findValueRequestDescriptor = $convert.base64Decode(
    'ChBGaW5kVmFsdWVSZXF1ZXN0Eh8KC3RhcmdldF91dWlkGAEgASgJUgp0YXJnZXRVdWlk');

@$core.Deprecated('Use findValueResponseDescriptor instead')
const FindValueResponse$json = {
  '1': 'FindValueResponse',
  '2': [
    {
      '1': 'sessions',
      '3': 1,
      '4': 3,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.UserSessionInfo',
      '10': 'sessions'
    },
    {
      '1': 'closest_nodes',
      '3': 2,
      '4': 3,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.NodeInfo',
      '10': 'closestNodes'
    },
    {
      '1': 'home_node_claim',
      '3': 3,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.HomeNodeClaim',
      '10': 'homeNodeClaim'
    },
    {
      '1': 'replacement_notice',
      '3': 4,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.IdentityReplacementNotice',
      '10': 'replacementNotice'
    },
  ],
};

/// Descriptor for `FindValueResponse`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List findValueResponseDescriptor = $convert.base64Decode(
    'ChFGaW5kVmFsdWVSZXNwb25zZRJACghzZXNzaW9ucxgBIAMoCzIkLmJyLmRldi5pc3BjaGF0Lm'
    'dycGMuVXNlclNlc3Npb25JbmZvUghzZXNzaW9ucxJCCg1jbG9zZXN0X25vZGVzGAIgAygLMh0u'
    'YnIuZGV2LmlzcGNoYXQuZ3JwYy5Ob2RlSW5mb1IMY2xvc2VzdE5vZGVzEkoKD2hvbWVfbm9kZV'
    '9jbGFpbRgDIAEoCzIiLmJyLmRldi5pc3BjaGF0LmdycGMuSG9tZU5vZGVDbGFpbVINaG9tZU5v'
    'ZGVDbGFpbRJdChJyZXBsYWNlbWVudF9ub3RpY2UYBCABKAsyLi5ici5kZXYuaXNwY2hhdC5ncn'
    'BjLklkZW50aXR5UmVwbGFjZW1lbnROb3RpY2VSEXJlcGxhY2VtZW50Tm90aWNl');

@$core.Deprecated('Use routePayloadRequestDescriptor instead')
const RoutePayloadRequest$json = {
  '1': 'RoutePayloadRequest',
  '2': [
    {'1': 'message_id', '3': 1, '4': 1, '5': 9, '10': 'messageId'},
    {'1': 'sender_uuid', '3': 2, '4': 1, '5': 9, '10': 'senderUuid'},
    {'1': 'receiver_uuid', '3': 3, '4': 1, '5': 9, '10': 'receiverUuid'},
    {
      '1': 'type',
      '3': 4,
      '4': 1,
      '5': 14,
      '6': '.br.dev.ispchat.grpc.MessageType',
      '10': 'type'
    },
    {
      '1': 'encrypted_payload',
      '3': 5,
      '4': 1,
      '5': 12,
      '10': 'encryptedPayload'
    },
    {
      '1': 'sender_public_key',
      '3': 6,
      '4': 1,
      '5': 12,
      '10': 'senderPublicKey'
    },
    {'1': 'signature', '3': 7, '4': 1, '5': 12, '10': 'signature'},
    {'1': 'timestamp', '3': 8, '4': 1, '5': 3, '10': 'timestamp'},
    {'1': 'sender_device_id', '3': 9, '4': 1, '5': 9, '10': 'senderDeviceId'},
    {
      '1': 'sender_delegation_cert',
      '3': 10,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.DeviceDelegationCert',
      '10': 'senderDelegationCert'
    },
    {
      '1': 'sender_verification_credential',
      '3': 11,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.VerificationCredential',
      '10': 'senderVerificationCredential'
    },
    {'1': 'ref_message_id', '3': 12, '4': 1, '5': 9, '10': 'refMessageId'},
    {'1': 'hop_count', '3': 13, '4': 1, '5': 13, '10': 'hopCount'},
  ],
};

/// Descriptor for `RoutePayloadRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List routePayloadRequestDescriptor = $convert.base64Decode(
    'ChNSb3V0ZVBheWxvYWRSZXF1ZXN0Eh0KCm1lc3NhZ2VfaWQYASABKAlSCW1lc3NhZ2VJZBIfCg'
    'tzZW5kZXJfdXVpZBgCIAEoCVIKc2VuZGVyVXVpZBIjCg1yZWNlaXZlcl91dWlkGAMgASgJUgxy'
    'ZWNlaXZlclV1aWQSNAoEdHlwZRgEIAEoDjIgLmJyLmRldi5pc3BjaGF0LmdycGMuTWVzc2FnZV'
    'R5cGVSBHR5cGUSKwoRZW5jcnlwdGVkX3BheWxvYWQYBSABKAxSEGVuY3J5cHRlZFBheWxvYWQS'
    'KgoRc2VuZGVyX3B1YmxpY19rZXkYBiABKAxSD3NlbmRlclB1YmxpY0tleRIcCglzaWduYXR1cm'
    'UYByABKAxSCXNpZ25hdHVyZRIcCgl0aW1lc3RhbXAYCCABKANSCXRpbWVzdGFtcBIoChBzZW5k'
    'ZXJfZGV2aWNlX2lkGAkgASgJUg5zZW5kZXJEZXZpY2VJZBJfChZzZW5kZXJfZGVsZWdhdGlvbl'
    '9jZXJ0GAogASgLMikuYnIuZGV2LmlzcGNoYXQuZ3JwYy5EZXZpY2VEZWxlZ2F0aW9uQ2VydFIU'
    'c2VuZGVyRGVsZWdhdGlvbkNlcnQScQoec2VuZGVyX3ZlcmlmaWNhdGlvbl9jcmVkZW50aWFsGA'
    'sgASgLMisuYnIuZGV2LmlzcGNoYXQuZ3JwYy5WZXJpZmljYXRpb25DcmVkZW50aWFsUhxzZW5k'
    'ZXJWZXJpZmljYXRpb25DcmVkZW50aWFsEiQKDnJlZl9tZXNzYWdlX2lkGAwgASgJUgxyZWZNZX'
    'NzYWdlSWQSGwoJaG9wX2NvdW50GA0gASgNUghob3BDb3VudA==');

@$core.Deprecated('Use routePayloadResponseDescriptor instead')
const RoutePayloadResponse$json = {
  '1': 'RoutePayloadResponse',
  '2': [
    {'1': 'delivered', '3': 1, '4': 1, '5': 8, '10': 'delivered'},
    {
      '1': 'status',
      '3': 2,
      '4': 1,
      '5': 14,
      '6': '.br.dev.ispchat.grpc.DeliveryStatus',
      '10': 'status'
    },
    {
      '1': 'delivered_device_ids',
      '3': 3,
      '4': 3,
      '5': 9,
      '10': 'deliveredDeviceIds'
    },
    {
      '1': 'redirect_home_node',
      '3': 4,
      '4': 1,
      '5': 9,
      '10': 'redirectHomeNode'
    },
  ],
};

/// Descriptor for `RoutePayloadResponse`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List routePayloadResponseDescriptor = $convert.base64Decode(
    'ChRSb3V0ZVBheWxvYWRSZXNwb25zZRIcCglkZWxpdmVyZWQYASABKAhSCWRlbGl2ZXJlZBI7Cg'
    'ZzdGF0dXMYAiABKA4yIy5ici5kZXYuaXNwY2hhdC5ncnBjLkRlbGl2ZXJ5U3RhdHVzUgZzdGF0'
    'dXMSMAoUZGVsaXZlcmVkX2RldmljZV9pZHMYAyADKAlSEmRlbGl2ZXJlZERldmljZUlkcxIsCh'
    'JyZWRpcmVjdF9ob21lX25vZGUYBCABKAlSEHJlZGlyZWN0SG9tZU5vZGU=');

@$core.Deprecated('Use turnCredentialRequestDescriptor instead')
const TurnCredentialRequest$json = {
  '1': 'TurnCredentialRequest',
  '2': [
    {'1': 'user_uuid', '3': 1, '4': 1, '5': 9, '10': 'userUuid'},
    {'1': 'device_id', '3': 2, '4': 1, '5': 9, '10': 'deviceId'},
    {'1': 'timestamp_ms', '3': 3, '4': 1, '5': 3, '10': 'timestampMs'},
    {
      '1': 'caller_public_key',
      '3': 4,
      '4': 1,
      '5': 12,
      '10': 'callerPublicKey'
    },
    {'1': 'signature', '3': 5, '4': 1, '5': 12, '10': 'signature'},
    {
      '1': 'delegation_cert',
      '3': 6,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.DeviceDelegationCert',
      '10': 'delegationCert'
    },
    {
      '1': 'verification_credential',
      '3': 7,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.VerificationCredential',
      '10': 'verificationCredential'
    },
  ],
};

/// Descriptor for `TurnCredentialRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List turnCredentialRequestDescriptor = $convert.base64Decode(
    'ChVUdXJuQ3JlZGVudGlhbFJlcXVlc3QSGwoJdXNlcl91dWlkGAEgASgJUgh1c2VyVXVpZBIbCg'
    'lkZXZpY2VfaWQYAiABKAlSCGRldmljZUlkEiEKDHRpbWVzdGFtcF9tcxgDIAEoA1ILdGltZXN0'
    'YW1wTXMSKgoRY2FsbGVyX3B1YmxpY19rZXkYBCABKAxSD2NhbGxlclB1YmxpY0tleRIcCglzaW'
    'duYXR1cmUYBSABKAxSCXNpZ25hdHVyZRJSCg9kZWxlZ2F0aW9uX2NlcnQYBiABKAsyKS5ici5k'
    'ZXYuaXNwY2hhdC5ncnBjLkRldmljZURlbGVnYXRpb25DZXJ0Ug5kZWxlZ2F0aW9uQ2VydBJkCh'
    'd2ZXJpZmljYXRpb25fY3JlZGVudGlhbBgHIAEoCzIrLmJyLmRldi5pc3BjaGF0LmdycGMuVmVy'
    'aWZpY2F0aW9uQ3JlZGVudGlhbFIWdmVyaWZpY2F0aW9uQ3JlZGVudGlhbA==');

@$core.Deprecated('Use turnCredentialResponseDescriptor instead')
const TurnCredentialResponse$json = {
  '1': 'TurnCredentialResponse',
  '2': [
    {'1': 'uris', '3': 1, '4': 3, '5': 9, '10': 'uris'},
    {'1': 'username', '3': 2, '4': 1, '5': 9, '10': 'username'},
    {'1': 'credential', '3': 3, '4': 1, '5': 9, '10': 'credential'},
    {'1': 'ttl_seconds', '3': 4, '4': 1, '5': 5, '10': 'ttlSeconds'},
    {'1': 'max_bps', '3': 5, '4': 1, '5': 5, '10': 'maxBps'},
  ],
};

/// Descriptor for `TurnCredentialResponse`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List turnCredentialResponseDescriptor = $convert.base64Decode(
    'ChZUdXJuQ3JlZGVudGlhbFJlc3BvbnNlEhIKBHVyaXMYASADKAlSBHVyaXMSGgoIdXNlcm5hbW'
    'UYAiABKAlSCHVzZXJuYW1lEh4KCmNyZWRlbnRpYWwYAyABKAlSCmNyZWRlbnRpYWwSHwoLdHRs'
    'X3NlY29uZHMYBCABKAVSCnR0bFNlY29uZHMSFwoHbWF4X2JwcxgFIAEoBVIGbWF4QnBz');

@$core.Deprecated('Use searchCnpjRequestDescriptor instead')
const SearchCnpjRequest$json = {
  '1': 'SearchCnpjRequest',
  '2': [
    {'1': 'query', '3': 1, '4': 1, '5': 9, '10': 'query'},
    {'1': 'limit', '3': 2, '4': 1, '5': 5, '10': 'limit'},
  ],
};

/// Descriptor for `SearchCnpjRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List searchCnpjRequestDescriptor = $convert.base64Decode(
    'ChFTZWFyY2hDbnBqUmVxdWVzdBIUCgVxdWVyeRgBIAEoCVIFcXVlcnkSFAoFbGltaXQYAiABKA'
    'VSBWxpbWl0');

@$core.Deprecated('Use verifiedCorporateEntryDescriptor instead')
const VerifiedCorporateEntry$json = {
  '1': 'VerifiedCorporateEntry',
  '2': [
    {'1': 'corporate_uuid', '3': 1, '4': 1, '5': 9, '10': 'corporateUuid'},
    {'1': 'cnpj', '3': 2, '4': 1, '5': 9, '10': 'cnpj'},
    {'1': 'display_name', '3': 3, '4': 1, '5': 9, '10': 'displayName'},
    {'1': 'home_node_address', '3': 4, '4': 1, '5': 9, '10': 'homeNodeAddress'},
    {'1': 'root_ed25519_pub', '3': 5, '4': 1, '5': 12, '10': 'rootEd25519Pub'},
    {'1': 'root_x25519_pub', '3': 6, '4': 1, '5': 12, '10': 'rootX25519Pub'},
    {
      '1': 'verification_credential',
      '3': 7,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.VerificationCredential',
      '10': 'verificationCredential'
    },
  ],
};

/// Descriptor for `VerifiedCorporateEntry`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List verifiedCorporateEntryDescriptor = $convert.base64Decode(
    'ChZWZXJpZmllZENvcnBvcmF0ZUVudHJ5EiUKDmNvcnBvcmF0ZV91dWlkGAEgASgJUg1jb3Jwb3'
    'JhdGVVdWlkEhIKBGNucGoYAiABKAlSBGNucGoSIQoMZGlzcGxheV9uYW1lGAMgASgJUgtkaXNw'
    'bGF5TmFtZRIqChFob21lX25vZGVfYWRkcmVzcxgEIAEoCVIPaG9tZU5vZGVBZGRyZXNzEigKEH'
    'Jvb3RfZWQyNTUxOV9wdWIYBSABKAxSDnJvb3RFZDI1NTE5UHViEiYKD3Jvb3RfeDI1NTE5X3B1'
    'YhgGIAEoDFINcm9vdFgyNTUxOVB1YhJkChd2ZXJpZmljYXRpb25fY3JlZGVudGlhbBgHIAEoCz'
    'IrLmJyLmRldi5pc3BjaGF0LmdycGMuVmVyaWZpY2F0aW9uQ3JlZGVudGlhbFIWdmVyaWZpY2F0'
    'aW9uQ3JlZGVudGlhbA==');

@$core.Deprecated('Use searchCnpjResponseDescriptor instead')
const SearchCnpjResponse$json = {
  '1': 'SearchCnpjResponse',
  '2': [
    {
      '1': 'entries',
      '3': 1,
      '4': 3,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.VerifiedCorporateEntry',
      '10': 'entries'
    },
  ],
};

/// Descriptor for `SearchCnpjResponse`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List searchCnpjResponseDescriptor = $convert.base64Decode(
    'ChJTZWFyY2hDbnBqUmVzcG9uc2USRQoHZW50cmllcxgBIAMoCzIrLmJyLmRldi5pc3BjaGF0Lm'
    'dycGMuVmVyaWZpZWRDb3Jwb3JhdGVFbnRyeVIHZW50cmllcw==');

@$core.Deprecated('Use partnerSeedEntryDescriptor instead')
const PartnerSeedEntry$json = {
  '1': 'PartnerSeedEntry',
  '2': [
    {'1': 'partner_uuid', '3': 1, '4': 1, '5': 9, '10': 'partnerUuid'},
    {'1': 'partner_cpf', '3': 2, '4': 1, '5': 9, '10': 'partnerCpf'},
    {
      '1': 'partner_ed25519_pub',
      '3': 3,
      '4': 1,
      '5': 12,
      '10': 'partnerEd25519Pub'
    },
    {
      '1': 'partner_x25519_pub',
      '3': 4,
      '4': 1,
      '5': 12,
      '10': 'partnerX25519Pub'
    },
  ],
};

/// Descriptor for `PartnerSeedEntry`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List partnerSeedEntryDescriptor = $convert.base64Decode(
    'ChBQYXJ0bmVyU2VlZEVudHJ5EiEKDHBhcnRuZXJfdXVpZBgBIAEoCVILcGFydG5lclV1aWQSHw'
    'oLcGFydG5lcl9jcGYYAiABKAlSCnBhcnRuZXJDcGYSLgoTcGFydG5lcl9lZDI1NTE5X3B1YhgD'
    'IAEoDFIRcGFydG5lckVkMjU1MTlQdWISLAoScGFydG5lcl94MjU1MTlfcHViGAQgASgMUhBwYX'
    'J0bmVyWDI1NTE5UHVi');

@$core.Deprecated('Use registerPartnerIspRequestDescriptor instead')
const RegisterPartnerIspRequest$json = {
  '1': 'RegisterPartnerIspRequest',
  '2': [
    {'1': 'isp_cnpj', '3': 1, '4': 1, '5': 9, '10': 'ispCnpj'},
    {'1': 'trade_name', '3': 2, '4': 1, '5': 9, '10': 'tradeName'},
    {'1': 'corporate_uuid', '3': 3, '4': 1, '5': 9, '10': 'corporateUuid'},
    {
      '1': 'corp_root_ed25519_pub',
      '3': 4,
      '4': 1,
      '5': 12,
      '10': 'corpRootEd25519Pub'
    },
    {
      '1': 'corp_root_x25519_pub',
      '3': 5,
      '4': 1,
      '5': 12,
      '10': 'corpRootX25519Pub'
    },
    {'1': 'node_address', '3': 6, '4': 1, '5': 9, '10': 'nodeAddress'},
    {
      '1': 'managing_partners',
      '3': 7,
      '4': 3,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.PartnerSeedEntry',
      '10': 'managingPartners'
    },
    {'1': 'issued_at_ms', '3': 8, '4': 1, '5': 3, '10': 'issuedAtMs'},
    {'1': 'root_signature', '3': 9, '4': 1, '5': 12, '10': 'rootSignature'},
  ],
};

/// Descriptor for `RegisterPartnerIspRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List registerPartnerIspRequestDescriptor = $convert.base64Decode(
    'ChlSZWdpc3RlclBhcnRuZXJJc3BSZXF1ZXN0EhkKCGlzcF9jbnBqGAEgASgJUgdpc3BDbnBqEh'
    '0KCnRyYWRlX25hbWUYAiABKAlSCXRyYWRlTmFtZRIlCg5jb3Jwb3JhdGVfdXVpZBgDIAEoCVIN'
    'Y29ycG9yYXRlVXVpZBIxChVjb3JwX3Jvb3RfZWQyNTUxOV9wdWIYBCABKAxSEmNvcnBSb290RW'
    'QyNTUxOVB1YhIvChRjb3JwX3Jvb3RfeDI1NTE5X3B1YhgFIAEoDFIRY29ycFJvb3RYMjU1MTlQ'
    'dWISIQoMbm9kZV9hZGRyZXNzGAYgASgJUgtub2RlQWRkcmVzcxJSChFtYW5hZ2luZ19wYXJ0bm'
    'VycxgHIAMoCzIlLmJyLmRldi5pc3BjaGF0LmdycGMuUGFydG5lclNlZWRFbnRyeVIQbWFuYWdp'
    'bmdQYXJ0bmVycxIgCgxpc3N1ZWRfYXRfbXMYCCABKANSCmlzc3VlZEF0TXMSJQoOcm9vdF9zaW'
    'duYXR1cmUYCSABKAxSDXJvb3RTaWduYXR1cmU=');

@$core.Deprecated('Use verificationBundleDescriptor instead')
const VerificationBundle$json = {
  '1': 'VerificationBundle',
  '2': [
    {
      '1': 'credential',
      '3': 1,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.VerificationCredential',
      '10': 'credential'
    },
    {
      '1': 'home_node_claim',
      '3': 2,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.HomeNodeClaim',
      '10': 'homeNodeClaim'
    },
    {
      '1': 'replacement_notice',
      '3': 3,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.IdentityReplacementNotice',
      '10': 'replacementNotice'
    },
  ],
};

/// Descriptor for `VerificationBundle`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List verificationBundleDescriptor = $convert.base64Decode(
    'ChJWZXJpZmljYXRpb25CdW5kbGUSSwoKY3JlZGVudGlhbBgBIAEoCzIrLmJyLmRldi5pc3BjaG'
    'F0LmdycGMuVmVyaWZpY2F0aW9uQ3JlZGVudGlhbFIKY3JlZGVudGlhbBJKCg9ob21lX25vZGVf'
    'Y2xhaW0YAiABKAsyIi5ici5kZXYuaXNwY2hhdC5ncnBjLkhvbWVOb2RlQ2xhaW1SDWhvbWVOb2'
    'RlQ2xhaW0SXQoScmVwbGFjZW1lbnRfbm90aWNlGAMgASgLMi4uYnIuZGV2LmlzcGNoYXQuZ3Jw'
    'Yy5JZGVudGl0eVJlcGxhY2VtZW50Tm90aWNlUhFyZXBsYWNlbWVudE5vdGljZQ==');

@$core.Deprecated('Use governanceAckDescriptor instead')
const GovernanceAck$json = {
  '1': 'GovernanceAck',
  '2': [
    {'1': 'accepted', '3': 1, '4': 1, '5': 8, '10': 'accepted'},
    {'1': 'message', '3': 2, '4': 1, '5': 9, '10': 'message'},
    {
      '1': 'corporate_bundle',
      '3': 3,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.VerificationBundle',
      '10': 'corporateBundle'
    },
  ],
};

/// Descriptor for `GovernanceAck`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List governanceAckDescriptor = $convert.base64Decode(
    'Cg1Hb3Zlcm5hbmNlQWNrEhoKCGFjY2VwdGVkGAEgASgIUghhY2NlcHRlZBIYCgdtZXNzYWdlGA'
    'IgASgJUgdtZXNzYWdlElIKEGNvcnBvcmF0ZV9idW5kbGUYAyABKAsyJy5ici5kZXYuaXNwY2hh'
    'dC5ncnBjLlZlcmlmaWNhdGlvbkJ1bmRsZVIPY29ycG9yYXRlQnVuZGxl');

@$core.Deprecated('Use authorizeCollaboratorRequestDescriptor instead')
const AuthorizeCollaboratorRequest$json = {
  '1': 'AuthorizeCollaboratorRequest',
  '2': [
    {
      '1': 'cert',
      '3': 1,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.CollaboratorAuthCert',
      '10': 'cert'
    },
    {'1': 'collaborator_cpf', '3': 2, '4': 1, '5': 9, '10': 'collaboratorCpf'},
  ],
};

/// Descriptor for `AuthorizeCollaboratorRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List authorizeCollaboratorRequestDescriptor =
    $convert.base64Decode(
        'ChxBdXRob3JpemVDb2xsYWJvcmF0b3JSZXF1ZXN0Ej0KBGNlcnQYASABKAsyKS5ici5kZXYuaX'
        'NwY2hhdC5ncnBjLkNvbGxhYm9yYXRvckF1dGhDZXJ0UgRjZXJ0EikKEGNvbGxhYm9yYXRvcl9j'
        'cGYYAiABKAlSD2NvbGxhYm9yYXRvckNwZg==');

@$core.Deprecated('Use revokeCollaboratorRequestDescriptor instead')
const RevokeCollaboratorRequest$json = {
  '1': 'RevokeCollaboratorRequest',
  '2': [
    {'1': 'isp_cnpj', '3': 1, '4': 1, '5': 9, '10': 'ispCnpj'},
    {'1': 'partner_uuid', '3': 2, '4': 1, '5': 9, '10': 'partnerUuid'},
    {
      '1': 'collaborator_uuid',
      '3': 3,
      '4': 1,
      '5': 9,
      '10': 'collaboratorUuid'
    },
    {'1': 'reason', '3': 4, '4': 1, '5': 9, '10': 'reason'},
    {'1': 'timestamp_ms', '3': 5, '4': 1, '5': 3, '10': 'timestampMs'},
    {
      '1': 'partner_signature',
      '3': 6,
      '4': 1,
      '5': 12,
      '10': 'partnerSignature'
    },
  ],
};

/// Descriptor for `RevokeCollaboratorRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List revokeCollaboratorRequestDescriptor = $convert.base64Decode(
    'ChlSZXZva2VDb2xsYWJvcmF0b3JSZXF1ZXN0EhkKCGlzcF9jbnBqGAEgASgJUgdpc3BDbnBqEi'
    'EKDHBhcnRuZXJfdXVpZBgCIAEoCVILcGFydG5lclV1aWQSKwoRY29sbGFib3JhdG9yX3V1aWQY'
    'AyABKAlSEGNvbGxhYm9yYXRvclV1aWQSFgoGcmVhc29uGAQgASgJUgZyZWFzb24SIQoMdGltZX'
    'N0YW1wX21zGAUgASgDUgt0aW1lc3RhbXBNcxIrChFwYXJ0bmVyX3NpZ25hdHVyZRgGIAEoDFIQ'
    'cGFydG5lclNpZ25hdHVyZQ==');

@$core.Deprecated('Use userVerificationConsentDescriptor instead')
const UserVerificationConsent$json = {
  '1': 'UserVerificationConsent',
  '2': [
    {'1': 'user_uuid', '3': 1, '4': 1, '5': 9, '10': 'userUuid'},
    {'1': 'user_ed25519_pub', '3': 2, '4': 1, '5': 12, '10': 'userEd25519Pub'},
    {'1': 'user_x25519_pub', '3': 3, '4': 1, '5': 12, '10': 'userX25519Pub'},
    {
      '1': 'doc_type',
      '3': 4,
      '4': 1,
      '5': 14,
      '6': '.br.dev.ispchat.grpc.DocumentType',
      '10': 'docType'
    },
    {'1': 'doc_sha256', '3': 5, '4': 1, '5': 12, '10': 'docSha256'},
    {'1': 'isp_cnpj', '3': 6, '4': 1, '5': 9, '10': 'ispCnpj'},
    {'1': 'timestamp_ms', '3': 7, '4': 1, '5': 3, '10': 'timestampMs'},
    {'1': 'user_signature', '3': 8, '4': 1, '5': 12, '10': 'userSignature'},
  ],
};

/// Descriptor for `UserVerificationConsent`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List userVerificationConsentDescriptor = $convert.base64Decode(
    'ChdVc2VyVmVyaWZpY2F0aW9uQ29uc2VudBIbCgl1c2VyX3V1aWQYASABKAlSCHVzZXJVdWlkEi'
    'gKEHVzZXJfZWQyNTUxOV9wdWIYAiABKAxSDnVzZXJFZDI1NTE5UHViEiYKD3VzZXJfeDI1NTE5'
    'X3B1YhgDIAEoDFINdXNlclgyNTUxOVB1YhI8Cghkb2NfdHlwZRgEIAEoDjIhLmJyLmRldi5pc3'
    'BjaGF0LmdycGMuRG9jdW1lbnRUeXBlUgdkb2NUeXBlEh0KCmRvY19zaGEyNTYYBSABKAxSCWRv'
    'Y1NoYTI1NhIZCghpc3BfY25wahgGIAEoCVIHaXNwQ25wahIhCgx0aW1lc3RhbXBfbXMYByABKA'
    'NSC3RpbWVzdGFtcE1zEiUKDnVzZXJfc2lnbmF0dXJlGAggASgMUg11c2VyU2lnbmF0dXJl');

@$core.Deprecated('Use submitVerificationRequestDescriptor instead')
const SubmitVerificationRequest$json = {
  '1': 'SubmitVerificationRequest',
  '2': [
    {'1': 'isp_cnpj', '3': 1, '4': 1, '5': 9, '10': 'ispCnpj'},
    {
      '1': 'collaborator_cert',
      '3': 2,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.CollaboratorAuthCert',
      '10': 'collaboratorCert'
    },
    {
      '1': 'user_consent',
      '3': 3,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.UserVerificationConsent',
      '10': 'userConsent'
    },
    {
      '1': 'normalized_document',
      '3': 4,
      '4': 1,
      '5': 9,
      '10': 'normalizedDocument'
    },
    {
      '1': 'public_display_name',
      '3': 5,
      '4': 1,
      '5': 9,
      '10': 'publicDisplayName'
    },
    {'1': 'home_node_address', '3': 6, '4': 1, '5': 9, '10': 'homeNodeAddress'},
    {'1': 'attested_at_ms', '3': 7, '4': 1, '5': 3, '10': 'attestedAtMs'},
    {
      '1': 'collaborator_signature',
      '3': 8,
      '4': 1,
      '5': 12,
      '10': 'collaboratorSignature'
    },
  ],
};

/// Descriptor for `SubmitVerificationRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List submitVerificationRequestDescriptor = $convert.base64Decode(
    'ChlTdWJtaXRWZXJpZmljYXRpb25SZXF1ZXN0EhkKCGlzcF9jbnBqGAEgASgJUgdpc3BDbnBqEl'
    'YKEWNvbGxhYm9yYXRvcl9jZXJ0GAIgASgLMikuYnIuZGV2LmlzcGNoYXQuZ3JwYy5Db2xsYWJv'
    'cmF0b3JBdXRoQ2VydFIQY29sbGFib3JhdG9yQ2VydBJPCgx1c2VyX2NvbnNlbnQYAyABKAsyLC'
    '5ici5kZXYuaXNwY2hhdC5ncnBjLlVzZXJWZXJpZmljYXRpb25Db25zZW50Ugt1c2VyQ29uc2Vu'
    'dBIvChNub3JtYWxpemVkX2RvY3VtZW50GAQgASgJUhJub3JtYWxpemVkRG9jdW1lbnQSLgoTcH'
    'VibGljX2Rpc3BsYXlfbmFtZRgFIAEoCVIRcHVibGljRGlzcGxheU5hbWUSKgoRaG9tZV9ub2Rl'
    'X2FkZHJlc3MYBiABKAlSD2hvbWVOb2RlQWRkcmVzcxIkCg5hdHRlc3RlZF9hdF9tcxgHIAEoA1'
    'IMYXR0ZXN0ZWRBdE1zEjUKFmNvbGxhYm9yYXRvcl9zaWduYXR1cmUYCCABKAxSFWNvbGxhYm9y'
    'YXRvclNpZ25hdHVyZQ==');

@$core.Deprecated('Use submitCpfRebindRequestDescriptor instead')
const SubmitCpfRebindRequest$json = {
  '1': 'SubmitCpfRebindRequest',
  '2': [
    {'1': 'isp_cnpj', '3': 1, '4': 1, '5': 9, '10': 'ispCnpj'},
    {
      '1': 'collaborator_cert',
      '3': 2,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.CollaboratorAuthCert',
      '10': 'collaboratorCert'
    },
    {'1': 'old_user_uuid', '3': 3, '4': 1, '5': 9, '10': 'oldUserUuid'},
    {
      '1': 'new_user_consent',
      '3': 4,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.UserVerificationConsent',
      '10': 'newUserConsent'
    },
    {'1': 'normalized_cpf', '3': 5, '4': 1, '5': 9, '10': 'normalizedCpf'},
    {'1': 'home_node_address', '3': 6, '4': 1, '5': 9, '10': 'homeNodeAddress'},
    {'1': 'attested_at_ms', '3': 7, '4': 1, '5': 3, '10': 'attestedAtMs'},
    {
      '1': 'collaborator_signature',
      '3': 8,
      '4': 1,
      '5': 12,
      '10': 'collaboratorSignature'
    },
  ],
};

/// Descriptor for `SubmitCpfRebindRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List submitCpfRebindRequestDescriptor = $convert.base64Decode(
    'ChZTdWJtaXRDcGZSZWJpbmRSZXF1ZXN0EhkKCGlzcF9jbnBqGAEgASgJUgdpc3BDbnBqElYKEW'
    'NvbGxhYm9yYXRvcl9jZXJ0GAIgASgLMikuYnIuZGV2LmlzcGNoYXQuZ3JwYy5Db2xsYWJvcmF0'
    'b3JBdXRoQ2VydFIQY29sbGFib3JhdG9yQ2VydBIiCg1vbGRfdXNlcl91dWlkGAMgASgJUgtvbG'
    'RVc2VyVXVpZBJWChBuZXdfdXNlcl9jb25zZW50GAQgASgLMiwuYnIuZGV2LmlzcGNoYXQuZ3Jw'
    'Yy5Vc2VyVmVyaWZpY2F0aW9uQ29uc2VudFIObmV3VXNlckNvbnNlbnQSJQoObm9ybWFsaXplZF'
    '9jcGYYBSABKAlSDW5vcm1hbGl6ZWRDcGYSKgoRaG9tZV9ub2RlX2FkZHJlc3MYBiABKAlSD2hv'
    'bWVOb2RlQWRkcmVzcxIkCg5hdHRlc3RlZF9hdF9tcxgHIAEoA1IMYXR0ZXN0ZWRBdE1zEjUKFm'
    'NvbGxhYm9yYXRvcl9zaWduYXR1cmUYCCABKAxSFWNvbGxhYm9yYXRvclNpZ25hdHVyZQ==');

@$core.Deprecated('Use getCredentialRequestDescriptor instead')
const GetCredentialRequest$json = {
  '1': 'GetCredentialRequest',
  '2': [
    {'1': 'user_uuid', '3': 1, '4': 1, '5': 9, '10': 'userUuid'},
    {'1': 'device_id', '3': 2, '4': 1, '5': 9, '10': 'deviceId'},
    {'1': 'timestamp_ms', '3': 3, '4': 1, '5': 3, '10': 'timestampMs'},
    {
      '1': 'caller_public_key',
      '3': 4,
      '4': 1,
      '5': 12,
      '10': 'callerPublicKey'
    },
    {'1': 'signature', '3': 5, '4': 1, '5': 12, '10': 'signature'},
    {
      '1': 'delegation_cert',
      '3': 6,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.DeviceDelegationCert',
      '10': 'delegationCert'
    },
  ],
};

/// Descriptor for `GetCredentialRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List getCredentialRequestDescriptor = $convert.base64Decode(
    'ChRHZXRDcmVkZW50aWFsUmVxdWVzdBIbCgl1c2VyX3V1aWQYASABKAlSCHVzZXJVdWlkEhsKCW'
    'RldmljZV9pZBgCIAEoCVIIZGV2aWNlSWQSIQoMdGltZXN0YW1wX21zGAMgASgDUgt0aW1lc3Rh'
    'bXBNcxIqChFjYWxsZXJfcHVibGljX2tleRgEIAEoDFIPY2FsbGVyUHVibGljS2V5EhwKCXNpZ2'
    '5hdHVyZRgFIAEoDFIJc2lnbmF0dXJlElIKD2RlbGVnYXRpb25fY2VydBgGIAEoCzIpLmJyLmRl'
    'di5pc3BjaGF0LmdycGMuRGV2aWNlRGVsZWdhdGlvbkNlcnRSDmRlbGVnYXRpb25DZXJ0');

@$core.Deprecated('Use revokeDeviceRequestDescriptor instead')
const RevokeDeviceRequest$json = {
  '1': 'RevokeDeviceRequest',
  '2': [
    {'1': 'user_uuid', '3': 1, '4': 1, '5': 9, '10': 'userUuid'},
    {'1': 'revoked_device_id', '3': 2, '4': 1, '5': 9, '10': 'revokedDeviceId'},
    {'1': 'reason', '3': 3, '4': 1, '5': 9, '10': 'reason'},
    {'1': 'timestamp_ms', '3': 4, '4': 1, '5': 3, '10': 'timestampMs'},
    {'1': 'root_signature', '3': 5, '4': 1, '5': 12, '10': 'rootSignature'},
  ],
};

/// Descriptor for `RevokeDeviceRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List revokeDeviceRequestDescriptor = $convert.base64Decode(
    'ChNSZXZva2VEZXZpY2VSZXF1ZXN0EhsKCXVzZXJfdXVpZBgBIAEoCVIIdXNlclV1aWQSKgoRcm'
    'V2b2tlZF9kZXZpY2VfaWQYAiABKAlSD3Jldm9rZWREZXZpY2VJZBIWCgZyZWFzb24YAyABKAlS'
    'BnJlYXNvbhIhCgx0aW1lc3RhbXBfbXMYBCABKANSC3RpbWVzdGFtcE1zEiUKDnJvb3Rfc2lnbm'
    'F0dXJlGAUgASgMUg1yb290U2lnbmF0dXJl');

@$core.Deprecated('Use portAccountRequestDescriptor instead')
const PortAccountRequest$json = {
  '1': 'PortAccountRequest',
  '2': [
    {'1': 'user_uuid', '3': 1, '4': 1, '5': 9, '10': 'userUuid'},
    {
      '1': 'target_home_node_address',
      '3': 2,
      '4': 1,
      '5': 9,
      '10': 'targetHomeNodeAddress'
    },
    {'1': 'target_isp_cnpj', '3': 3, '4': 1, '5': 9, '10': 'targetIspCnpj'},
    {
      '1': 'expected_routing_epoch',
      '3': 4,
      '4': 1,
      '5': 3,
      '10': 'expectedRoutingEpoch'
    },
    {'1': 'timestamp_ms', '3': 5, '4': 1, '5': 3, '10': 'timestampMs'},
    {'1': 'root_signature', '3': 6, '4': 1, '5': 12, '10': 'rootSignature'},
    {
      '1': 'target_collaborator_cert',
      '3': 7,
      '4': 1,
      '5': 11,
      '6': '.br.dev.ispchat.grpc.CollaboratorAuthCert',
      '10': 'targetCollaboratorCert'
    },
    {
      '1': 'target_collaborator_signature',
      '3': 8,
      '4': 1,
      '5': 12,
      '10': 'targetCollaboratorSignature'
    },
  ],
};

/// Descriptor for `PortAccountRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List portAccountRequestDescriptor = $convert.base64Decode(
    'ChJQb3J0QWNjb3VudFJlcXVlc3QSGwoJdXNlcl91dWlkGAEgASgJUgh1c2VyVXVpZBI3Chh0YX'
    'JnZXRfaG9tZV9ub2RlX2FkZHJlc3MYAiABKAlSFXRhcmdldEhvbWVOb2RlQWRkcmVzcxImCg90'
    'YXJnZXRfaXNwX2NucGoYAyABKAlSDXRhcmdldElzcENucGoSNAoWZXhwZWN0ZWRfcm91dGluZ1'
    '9lcG9jaBgEIAEoA1IUZXhwZWN0ZWRSb3V0aW5nRXBvY2gSIQoMdGltZXN0YW1wX21zGAUgASgD'
    'Ugt0aW1lc3RhbXBNcxIlCg5yb290X3NpZ25hdHVyZRgGIAEoDFINcm9vdFNpZ25hdHVyZRJjCh'
    'h0YXJnZXRfY29sbGFib3JhdG9yX2NlcnQYByABKAsyKS5ici5kZXYuaXNwY2hhdC5ncnBjLkNv'
    'bGxhYm9yYXRvckF1dGhDZXJ0UhZ0YXJnZXRDb2xsYWJvcmF0b3JDZXJ0EkIKHXRhcmdldF9jb2'
    'xsYWJvcmF0b3Jfc2lnbmF0dXJlGAggASgMUht0YXJnZXRDb2xsYWJvcmF0b3JTaWduYXR1cmU=');

@$core.Deprecated('Use streamRevocationsRequestDescriptor instead')
const StreamRevocationsRequest$json = {
  '1': 'StreamRevocationsRequest',
  '2': [
    {
      '1': 'since_revocation_seq',
      '3': 1,
      '4': 1,
      '5': 3,
      '10': 'sinceRevocationSeq'
    },
  ],
};

/// Descriptor for `StreamRevocationsRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List streamRevocationsRequestDescriptor =
    $convert.base64Decode(
        'ChhTdHJlYW1SZXZvY2F0aW9uc1JlcXVlc3QSMAoUc2luY2VfcmV2b2NhdGlvbl9zZXEYASABKA'
        'NSEnNpbmNlUmV2b2NhdGlvblNlcQ==');
