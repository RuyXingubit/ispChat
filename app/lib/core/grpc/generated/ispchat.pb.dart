// This is a generated file - do not edit.
//
// Generated from ispchat.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports

import 'dart:core' as $core;

import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:protobuf/protobuf.dart' as $pb;

import 'ispchat.pbenum.dart';

export 'package:protobuf/protobuf.dart' show GeneratedMessageGenericExtensions;

export 'ispchat.pbenum.dart';

/// Certificado do servidor Principal assinado offline pela chave Raiz Nivel 0
class PrincipalSigningCert extends $pb.GeneratedMessage {
  factory PrincipalSigningCert({
    $core.String? certId,
    $core.List<$core.int>? principalEd25519Pub,
    $core.List<$core.int>? escrowX25519Pub,
    $fixnum.Int64? issuedAtMs,
    $fixnum.Int64? expiresAtMs,
    $core.List<$core.int>? rootSignature,
  }) {
    final result = create();
    if (certId != null) result.certId = certId;
    if (principalEd25519Pub != null)
      result.principalEd25519Pub = principalEd25519Pub;
    if (escrowX25519Pub != null) result.escrowX25519Pub = escrowX25519Pub;
    if (issuedAtMs != null) result.issuedAtMs = issuedAtMs;
    if (expiresAtMs != null) result.expiresAtMs = expiresAtMs;
    if (rootSignature != null) result.rootSignature = rootSignature;
    return result;
  }

  PrincipalSigningCert._();

  factory PrincipalSigningCert.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory PrincipalSigningCert.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'PrincipalSigningCert',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'certId')
    ..a<$core.List<$core.int>>(
        2, _omitFieldNames ? '' : 'principalEd25519Pub', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        3, _omitFieldNames ? '' : 'escrowX25519Pub', $pb.PbFieldType.OY)
    ..aInt64(4, _omitFieldNames ? '' : 'issuedAtMs')
    ..aInt64(5, _omitFieldNames ? '' : 'expiresAtMs')
    ..a<$core.List<$core.int>>(
        6, _omitFieldNames ? '' : 'rootSignature', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PrincipalSigningCert clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PrincipalSigningCert copyWith(void Function(PrincipalSigningCert) updates) =>
      super.copyWith((message) => updates(message as PrincipalSigningCert))
          as PrincipalSigningCert;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static PrincipalSigningCert create() => PrincipalSigningCert._();
  @$core.override
  PrincipalSigningCert createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static PrincipalSigningCert getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<PrincipalSigningCert>(create);
  static PrincipalSigningCert? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get certId => $_getSZ(0);
  @$pb.TagNumber(1)
  set certId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasCertId() => $_has(0);
  @$pb.TagNumber(1)
  void clearCertId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.List<$core.int> get principalEd25519Pub => $_getN(1);
  @$pb.TagNumber(2)
  set principalEd25519Pub($core.List<$core.int> value) => $_setBytes(1, value);
  @$pb.TagNumber(2)
  $core.bool hasPrincipalEd25519Pub() => $_has(1);
  @$pb.TagNumber(2)
  void clearPrincipalEd25519Pub() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.List<$core.int> get escrowX25519Pub => $_getN(2);
  @$pb.TagNumber(3)
  set escrowX25519Pub($core.List<$core.int> value) => $_setBytes(2, value);
  @$pb.TagNumber(3)
  $core.bool hasEscrowX25519Pub() => $_has(2);
  @$pb.TagNumber(3)
  void clearEscrowX25519Pub() => $_clearField(3);

  @$pb.TagNumber(4)
  $fixnum.Int64 get issuedAtMs => $_getI64(3);
  @$pb.TagNumber(4)
  set issuedAtMs($fixnum.Int64 value) => $_setInt64(3, value);
  @$pb.TagNumber(4)
  $core.bool hasIssuedAtMs() => $_has(3);
  @$pb.TagNumber(4)
  void clearIssuedAtMs() => $_clearField(4);

  @$pb.TagNumber(5)
  $fixnum.Int64 get expiresAtMs => $_getI64(4);
  @$pb.TagNumber(5)
  set expiresAtMs($fixnum.Int64 value) => $_setInt64(4, value);
  @$pb.TagNumber(5)
  $core.bool hasExpiresAtMs() => $_has(4);
  @$pb.TagNumber(5)
  void clearExpiresAtMs() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.List<$core.int> get rootSignature => $_getN(5);
  @$pb.TagNumber(6)
  set rootSignature($core.List<$core.int> value) => $_setBytes(5, value);
  @$pb.TagNumber(6)
  $core.bool hasRootSignature() => $_has(5);
  @$pb.TagNumber(6)
  void clearRootSignature() => $_clearField(6);
}

/// Credencial auto-contida de conta verificada (validavel em memoria pelos nos ISP)
class VerificationCredential extends $pb.GeneratedMessage {
  factory VerificationCredential({
    $core.String? credentialId,
    $core.String? userUuid,
    $core.List<$core.int>? rootEd25519Pub,
    $core.List<$core.int>? rootX25519Pub,
    DocumentType? docType,
    $core.String? publicCnpj,
    $core.String? publicDisplayName,
    $core.String? verifyingIspCnpj,
    $core.String? verifyingIspName,
    $core.String? homeNodeAddress,
    $fixnum.Int64? routingEpoch,
    $fixnum.Int64? issuedAtMs,
    $fixnum.Int64? expiresAtMs,
    $core.List<$core.int>? principalSignature,
    PrincipalSigningCert? principalCert,
  }) {
    final result = create();
    if (credentialId != null) result.credentialId = credentialId;
    if (userUuid != null) result.userUuid = userUuid;
    if (rootEd25519Pub != null) result.rootEd25519Pub = rootEd25519Pub;
    if (rootX25519Pub != null) result.rootX25519Pub = rootX25519Pub;
    if (docType != null) result.docType = docType;
    if (publicCnpj != null) result.publicCnpj = publicCnpj;
    if (publicDisplayName != null) result.publicDisplayName = publicDisplayName;
    if (verifyingIspCnpj != null) result.verifyingIspCnpj = verifyingIspCnpj;
    if (verifyingIspName != null) result.verifyingIspName = verifyingIspName;
    if (homeNodeAddress != null) result.homeNodeAddress = homeNodeAddress;
    if (routingEpoch != null) result.routingEpoch = routingEpoch;
    if (issuedAtMs != null) result.issuedAtMs = issuedAtMs;
    if (expiresAtMs != null) result.expiresAtMs = expiresAtMs;
    if (principalSignature != null)
      result.principalSignature = principalSignature;
    if (principalCert != null) result.principalCert = principalCert;
    return result;
  }

  VerificationCredential._();

  factory VerificationCredential.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory VerificationCredential.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'VerificationCredential',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'credentialId')
    ..aOS(2, _omitFieldNames ? '' : 'userUuid')
    ..a<$core.List<$core.int>>(
        3, _omitFieldNames ? '' : 'rootEd25519Pub', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        4, _omitFieldNames ? '' : 'rootX25519Pub', $pb.PbFieldType.OY)
    ..aE<DocumentType>(5, _omitFieldNames ? '' : 'docType',
        enumValues: DocumentType.values)
    ..aOS(6, _omitFieldNames ? '' : 'publicCnpj')
    ..aOS(7, _omitFieldNames ? '' : 'publicDisplayName')
    ..aOS(8, _omitFieldNames ? '' : 'verifyingIspCnpj')
    ..aOS(9, _omitFieldNames ? '' : 'verifyingIspName')
    ..aOS(10, _omitFieldNames ? '' : 'homeNodeAddress')
    ..aInt64(11, _omitFieldNames ? '' : 'routingEpoch')
    ..aInt64(12, _omitFieldNames ? '' : 'issuedAtMs')
    ..aInt64(13, _omitFieldNames ? '' : 'expiresAtMs')
    ..a<$core.List<$core.int>>(
        14, _omitFieldNames ? '' : 'principalSignature', $pb.PbFieldType.OY)
    ..aOM<PrincipalSigningCert>(15, _omitFieldNames ? '' : 'principalCert',
        subBuilder: PrincipalSigningCert.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  VerificationCredential clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  VerificationCredential copyWith(
          void Function(VerificationCredential) updates) =>
      super.copyWith((message) => updates(message as VerificationCredential))
          as VerificationCredential;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static VerificationCredential create() => VerificationCredential._();
  @$core.override
  VerificationCredential createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static VerificationCredential getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<VerificationCredential>(create);
  static VerificationCredential? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get credentialId => $_getSZ(0);
  @$pb.TagNumber(1)
  set credentialId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasCredentialId() => $_has(0);
  @$pb.TagNumber(1)
  void clearCredentialId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get userUuid => $_getSZ(1);
  @$pb.TagNumber(2)
  set userUuid($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasUserUuid() => $_has(1);
  @$pb.TagNumber(2)
  void clearUserUuid() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.List<$core.int> get rootEd25519Pub => $_getN(2);
  @$pb.TagNumber(3)
  set rootEd25519Pub($core.List<$core.int> value) => $_setBytes(2, value);
  @$pb.TagNumber(3)
  $core.bool hasRootEd25519Pub() => $_has(2);
  @$pb.TagNumber(3)
  void clearRootEd25519Pub() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.List<$core.int> get rootX25519Pub => $_getN(3);
  @$pb.TagNumber(4)
  set rootX25519Pub($core.List<$core.int> value) => $_setBytes(3, value);
  @$pb.TagNumber(4)
  $core.bool hasRootX25519Pub() => $_has(3);
  @$pb.TagNumber(4)
  void clearRootX25519Pub() => $_clearField(4);

  @$pb.TagNumber(5)
  DocumentType get docType => $_getN(4);
  @$pb.TagNumber(5)
  set docType(DocumentType value) => $_setField(5, value);
  @$pb.TagNumber(5)
  $core.bool hasDocType() => $_has(4);
  @$pb.TagNumber(5)
  void clearDocType() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get publicCnpj => $_getSZ(5);
  @$pb.TagNumber(6)
  set publicCnpj($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasPublicCnpj() => $_has(5);
  @$pb.TagNumber(6)
  void clearPublicCnpj() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.String get publicDisplayName => $_getSZ(6);
  @$pb.TagNumber(7)
  set publicDisplayName($core.String value) => $_setString(6, value);
  @$pb.TagNumber(7)
  $core.bool hasPublicDisplayName() => $_has(6);
  @$pb.TagNumber(7)
  void clearPublicDisplayName() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.String get verifyingIspCnpj => $_getSZ(7);
  @$pb.TagNumber(8)
  set verifyingIspCnpj($core.String value) => $_setString(7, value);
  @$pb.TagNumber(8)
  $core.bool hasVerifyingIspCnpj() => $_has(7);
  @$pb.TagNumber(8)
  void clearVerifyingIspCnpj() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.String get verifyingIspName => $_getSZ(8);
  @$pb.TagNumber(9)
  set verifyingIspName($core.String value) => $_setString(8, value);
  @$pb.TagNumber(9)
  $core.bool hasVerifyingIspName() => $_has(8);
  @$pb.TagNumber(9)
  void clearVerifyingIspName() => $_clearField(9);

  @$pb.TagNumber(10)
  $core.String get homeNodeAddress => $_getSZ(9);
  @$pb.TagNumber(10)
  set homeNodeAddress($core.String value) => $_setString(9, value);
  @$pb.TagNumber(10)
  $core.bool hasHomeNodeAddress() => $_has(9);
  @$pb.TagNumber(10)
  void clearHomeNodeAddress() => $_clearField(10);

  @$pb.TagNumber(11)
  $fixnum.Int64 get routingEpoch => $_getI64(10);
  @$pb.TagNumber(11)
  set routingEpoch($fixnum.Int64 value) => $_setInt64(10, value);
  @$pb.TagNumber(11)
  $core.bool hasRoutingEpoch() => $_has(10);
  @$pb.TagNumber(11)
  void clearRoutingEpoch() => $_clearField(11);

  @$pb.TagNumber(12)
  $fixnum.Int64 get issuedAtMs => $_getI64(11);
  @$pb.TagNumber(12)
  set issuedAtMs($fixnum.Int64 value) => $_setInt64(11, value);
  @$pb.TagNumber(12)
  $core.bool hasIssuedAtMs() => $_has(11);
  @$pb.TagNumber(12)
  void clearIssuedAtMs() => $_clearField(12);

  @$pb.TagNumber(13)
  $fixnum.Int64 get expiresAtMs => $_getI64(12);
  @$pb.TagNumber(13)
  set expiresAtMs($fixnum.Int64 value) => $_setInt64(12, value);
  @$pb.TagNumber(13)
  $core.bool hasExpiresAtMs() => $_has(12);
  @$pb.TagNumber(13)
  void clearExpiresAtMs() => $_clearField(13);

  @$pb.TagNumber(14)
  $core.List<$core.int> get principalSignature => $_getN(13);
  @$pb.TagNumber(14)
  set principalSignature($core.List<$core.int> value) => $_setBytes(13, value);
  @$pb.TagNumber(14)
  $core.bool hasPrincipalSignature() => $_has(13);
  @$pb.TagNumber(14)
  void clearPrincipalSignature() => $_clearField(14);

  @$pb.TagNumber(15)
  PrincipalSigningCert get principalCert => $_getN(14);
  @$pb.TagNumber(15)
  set principalCert(PrincipalSigningCert value) => $_setField(15, value);
  @$pb.TagNumber(15)
  $core.bool hasPrincipalCert() => $_has(14);
  @$pb.TagNumber(15)
  void clearPrincipalCert() => $_clearField(15);
  @$pb.TagNumber(15)
  PrincipalSigningCert ensurePrincipalCert() => $_ensure(14);
}

/// Certificado de delegacao de sub-chave (DESKTOP ou ERP_API) assinado pela chave raiz movel
class DeviceDelegationCert extends $pb.GeneratedMessage {
  factory DeviceDelegationCert({
    $core.String? certId,
    $core.String? userUuid,
    $core.String? deviceId,
    DeviceRole? role,
    $core.List<$core.int>? deviceEd25519Pub,
    $core.List<$core.int>? deviceX25519Pub,
    $fixnum.Int64? issuedAtMs,
    $fixnum.Int64? expiresAtMs,
    $core.List<$core.int>? rootSignature,
  }) {
    final result = create();
    if (certId != null) result.certId = certId;
    if (userUuid != null) result.userUuid = userUuid;
    if (deviceId != null) result.deviceId = deviceId;
    if (role != null) result.role = role;
    if (deviceEd25519Pub != null) result.deviceEd25519Pub = deviceEd25519Pub;
    if (deviceX25519Pub != null) result.deviceX25519Pub = deviceX25519Pub;
    if (issuedAtMs != null) result.issuedAtMs = issuedAtMs;
    if (expiresAtMs != null) result.expiresAtMs = expiresAtMs;
    if (rootSignature != null) result.rootSignature = rootSignature;
    return result;
  }

  DeviceDelegationCert._();

  factory DeviceDelegationCert.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory DeviceDelegationCert.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'DeviceDelegationCert',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'certId')
    ..aOS(2, _omitFieldNames ? '' : 'userUuid')
    ..aOS(3, _omitFieldNames ? '' : 'deviceId')
    ..aE<DeviceRole>(4, _omitFieldNames ? '' : 'role',
        enumValues: DeviceRole.values)
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'deviceEd25519Pub', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        6, _omitFieldNames ? '' : 'deviceX25519Pub', $pb.PbFieldType.OY)
    ..aInt64(7, _omitFieldNames ? '' : 'issuedAtMs')
    ..aInt64(8, _omitFieldNames ? '' : 'expiresAtMs')
    ..a<$core.List<$core.int>>(
        9, _omitFieldNames ? '' : 'rootSignature', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DeviceDelegationCert clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DeviceDelegationCert copyWith(void Function(DeviceDelegationCert) updates) =>
      super.copyWith((message) => updates(message as DeviceDelegationCert))
          as DeviceDelegationCert;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static DeviceDelegationCert create() => DeviceDelegationCert._();
  @$core.override
  DeviceDelegationCert createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static DeviceDelegationCert getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<DeviceDelegationCert>(create);
  static DeviceDelegationCert? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get certId => $_getSZ(0);
  @$pb.TagNumber(1)
  set certId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasCertId() => $_has(0);
  @$pb.TagNumber(1)
  void clearCertId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get userUuid => $_getSZ(1);
  @$pb.TagNumber(2)
  set userUuid($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasUserUuid() => $_has(1);
  @$pb.TagNumber(2)
  void clearUserUuid() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get deviceId => $_getSZ(2);
  @$pb.TagNumber(3)
  set deviceId($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasDeviceId() => $_has(2);
  @$pb.TagNumber(3)
  void clearDeviceId() => $_clearField(3);

  @$pb.TagNumber(4)
  DeviceRole get role => $_getN(3);
  @$pb.TagNumber(4)
  set role(DeviceRole value) => $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasRole() => $_has(3);
  @$pb.TagNumber(4)
  void clearRole() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get deviceEd25519Pub => $_getN(4);
  @$pb.TagNumber(5)
  set deviceEd25519Pub($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasDeviceEd25519Pub() => $_has(4);
  @$pb.TagNumber(5)
  void clearDeviceEd25519Pub() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.List<$core.int> get deviceX25519Pub => $_getN(5);
  @$pb.TagNumber(6)
  set deviceX25519Pub($core.List<$core.int> value) => $_setBytes(5, value);
  @$pb.TagNumber(6)
  $core.bool hasDeviceX25519Pub() => $_has(5);
  @$pb.TagNumber(6)
  void clearDeviceX25519Pub() => $_clearField(6);

  @$pb.TagNumber(7)
  $fixnum.Int64 get issuedAtMs => $_getI64(6);
  @$pb.TagNumber(7)
  set issuedAtMs($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(7)
  $core.bool hasIssuedAtMs() => $_has(6);
  @$pb.TagNumber(7)
  void clearIssuedAtMs() => $_clearField(7);

  @$pb.TagNumber(8)
  $fixnum.Int64 get expiresAtMs => $_getI64(7);
  @$pb.TagNumber(8)
  set expiresAtMs($fixnum.Int64 value) => $_setInt64(7, value);
  @$pb.TagNumber(8)
  $core.bool hasExpiresAtMs() => $_has(7);
  @$pb.TagNumber(8)
  void clearExpiresAtMs() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.List<$core.int> get rootSignature => $_getN(8);
  @$pb.TagNumber(9)
  set rootSignature($core.List<$core.int> value) => $_setBytes(8, value);
  @$pb.TagNumber(9)
  $core.bool hasRootSignature() => $_has(8);
  @$pb.TagNumber(9)
  void clearRootSignature() => $_clearField(9);
}

/// Declaracao autenticada do No Casa responsavel pela fila offline da conta
class HomeNodeClaim extends $pb.GeneratedMessage {
  factory HomeNodeClaim({
    $core.String? userUuid,
    $core.String? homeNodeAddress,
    $fixnum.Int64? routingEpoch,
    $fixnum.Int64? issuedAtMs,
    $core.List<$core.int>? claimSignature,
    $core.bool? signedByPrincipal,
  }) {
    final result = create();
    if (userUuid != null) result.userUuid = userUuid;
    if (homeNodeAddress != null) result.homeNodeAddress = homeNodeAddress;
    if (routingEpoch != null) result.routingEpoch = routingEpoch;
    if (issuedAtMs != null) result.issuedAtMs = issuedAtMs;
    if (claimSignature != null) result.claimSignature = claimSignature;
    if (signedByPrincipal != null) result.signedByPrincipal = signedByPrincipal;
    return result;
  }

  HomeNodeClaim._();

  factory HomeNodeClaim.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory HomeNodeClaim.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'HomeNodeClaim',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'userUuid')
    ..aOS(2, _omitFieldNames ? '' : 'homeNodeAddress')
    ..aInt64(3, _omitFieldNames ? '' : 'routingEpoch')
    ..aInt64(4, _omitFieldNames ? '' : 'issuedAtMs')
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'claimSignature', $pb.PbFieldType.OY)
    ..aOB(6, _omitFieldNames ? '' : 'signedByPrincipal')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  HomeNodeClaim clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  HomeNodeClaim copyWith(void Function(HomeNodeClaim) updates) =>
      super.copyWith((message) => updates(message as HomeNodeClaim))
          as HomeNodeClaim;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static HomeNodeClaim create() => HomeNodeClaim._();
  @$core.override
  HomeNodeClaim createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static HomeNodeClaim getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<HomeNodeClaim>(create);
  static HomeNodeClaim? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get userUuid => $_getSZ(0);
  @$pb.TagNumber(1)
  set userUuid($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasUserUuid() => $_has(0);
  @$pb.TagNumber(1)
  void clearUserUuid() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get homeNodeAddress => $_getSZ(1);
  @$pb.TagNumber(2)
  set homeNodeAddress($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasHomeNodeAddress() => $_has(1);
  @$pb.TagNumber(2)
  void clearHomeNodeAddress() => $_clearField(2);

  @$pb.TagNumber(3)
  $fixnum.Int64 get routingEpoch => $_getI64(2);
  @$pb.TagNumber(3)
  set routingEpoch($fixnum.Int64 value) => $_setInt64(2, value);
  @$pb.TagNumber(3)
  $core.bool hasRoutingEpoch() => $_has(2);
  @$pb.TagNumber(3)
  void clearRoutingEpoch() => $_clearField(3);

  @$pb.TagNumber(4)
  $fixnum.Int64 get issuedAtMs => $_getI64(3);
  @$pb.TagNumber(4)
  set issuedAtMs($fixnum.Int64 value) => $_setInt64(3, value);
  @$pb.TagNumber(4)
  $core.bool hasIssuedAtMs() => $_has(3);
  @$pb.TagNumber(4)
  void clearIssuedAtMs() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get claimSignature => $_getN(4);
  @$pb.TagNumber(5)
  set claimSignature($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasClaimSignature() => $_has(4);
  @$pb.TagNumber(5)
  void clearClaimSignature() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.bool get signedByPrincipal => $_getBF(5);
  @$pb.TagNumber(6)
  set signedByPrincipal($core.bool value) => $_setBool(5, value);
  @$pb.TagNumber(6)
  $core.bool hasSignedByPrincipal() => $_has(5);
  @$pb.TagNumber(6)
  void clearSignedByPrincipal() => $_clearField(6);
}

/// Chave simetrica de conteudo embrulhada individualmente para um device_id alvo
class WrappedContentKey extends $pb.GeneratedMessage {
  factory WrappedContentKey({
    $core.String? targetDeviceId,
    $core.List<$core.int>? wrappedKeyBlob,
  }) {
    final result = create();
    if (targetDeviceId != null) result.targetDeviceId = targetDeviceId;
    if (wrappedKeyBlob != null) result.wrappedKeyBlob = wrappedKeyBlob;
    return result;
  }

  WrappedContentKey._();

  factory WrappedContentKey.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory WrappedContentKey.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'WrappedContentKey',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'targetDeviceId')
    ..a<$core.List<$core.int>>(
        2, _omitFieldNames ? '' : 'wrappedKeyBlob', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  WrappedContentKey clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  WrappedContentKey copyWith(void Function(WrappedContentKey) updates) =>
      super.copyWith((message) => updates(message as WrappedContentKey))
          as WrappedContentKey;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static WrappedContentKey create() => WrappedContentKey._();
  @$core.override
  WrappedContentKey createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static WrappedContentKey getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<WrappedContentKey>(create);
  static WrappedContentKey? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get targetDeviceId => $_getSZ(0);
  @$pb.TagNumber(1)
  set targetDeviceId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasTargetDeviceId() => $_has(0);
  @$pb.TagNumber(1)
  void clearTargetDeviceId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.List<$core.int> get wrappedKeyBlob => $_getN(1);
  @$pb.TagNumber(2)
  set wrappedKeyBlob($core.List<$core.int> value) => $_setBytes(1, value);
  @$pb.TagNumber(2)
  $core.bool hasWrappedKeyBlob() => $_has(1);
  @$pb.TagNumber(2)
  void clearWrappedKeyBlob() => $_clearField(2);
}

/// Carga util E2E multi-dispositivo (ChaCha20-Poly1305 + X25519 HKDF-SHA256 key wrapping)
class MultiDeviceEncryptedPayload extends $pb.GeneratedMessage {
  factory MultiDeviceEncryptedPayload({
    $core.List<$core.int>? ephemeralX25519Pub,
    $core.List<$core.int>? contentNonce,
    $core.List<$core.int>? ciphertextWithTag,
    $core.Iterable<WrappedContentKey>? wrappedKeys,
  }) {
    final result = create();
    if (ephemeralX25519Pub != null)
      result.ephemeralX25519Pub = ephemeralX25519Pub;
    if (contentNonce != null) result.contentNonce = contentNonce;
    if (ciphertextWithTag != null) result.ciphertextWithTag = ciphertextWithTag;
    if (wrappedKeys != null) result.wrappedKeys.addAll(wrappedKeys);
    return result;
  }

  MultiDeviceEncryptedPayload._();

  factory MultiDeviceEncryptedPayload.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory MultiDeviceEncryptedPayload.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'MultiDeviceEncryptedPayload',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..a<$core.List<$core.int>>(
        1, _omitFieldNames ? '' : 'ephemeralX25519Pub', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        2, _omitFieldNames ? '' : 'contentNonce', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        3, _omitFieldNames ? '' : 'ciphertextWithTag', $pb.PbFieldType.OY)
    ..pPM<WrappedContentKey>(4, _omitFieldNames ? '' : 'wrappedKeys',
        subBuilder: WrappedContentKey.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  MultiDeviceEncryptedPayload clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  MultiDeviceEncryptedPayload copyWith(
          void Function(MultiDeviceEncryptedPayload) updates) =>
      super.copyWith(
              (message) => updates(message as MultiDeviceEncryptedPayload))
          as MultiDeviceEncryptedPayload;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static MultiDeviceEncryptedPayload create() =>
      MultiDeviceEncryptedPayload._();
  @$core.override
  MultiDeviceEncryptedPayload createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static MultiDeviceEncryptedPayload getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<MultiDeviceEncryptedPayload>(create);
  static MultiDeviceEncryptedPayload? _defaultInstance;

  @$pb.TagNumber(1)
  $core.List<$core.int> get ephemeralX25519Pub => $_getN(0);
  @$pb.TagNumber(1)
  set ephemeralX25519Pub($core.List<$core.int> value) => $_setBytes(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEphemeralX25519Pub() => $_has(0);
  @$pb.TagNumber(1)
  void clearEphemeralX25519Pub() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.List<$core.int> get contentNonce => $_getN(1);
  @$pb.TagNumber(2)
  set contentNonce($core.List<$core.int> value) => $_setBytes(1, value);
  @$pb.TagNumber(2)
  $core.bool hasContentNonce() => $_has(1);
  @$pb.TagNumber(2)
  void clearContentNonce() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.List<$core.int> get ciphertextWithTag => $_getN(2);
  @$pb.TagNumber(3)
  set ciphertextWithTag($core.List<$core.int> value) => $_setBytes(2, value);
  @$pb.TagNumber(3)
  $core.bool hasCiphertextWithTag() => $_has(2);
  @$pb.TagNumber(3)
  void clearCiphertextWithTag() => $_clearField(3);

  @$pb.TagNumber(4)
  $pb.PbList<WrappedContentKey> get wrappedKeys => $_getList(3);
}

/// Aviso assinado pelo Principal comunicando rotacao auditada de UUIDv7 por perda de aparelho
class IdentityReplacementNotice extends $pb.GeneratedMessage {
  factory IdentityReplacementNotice({
    $core.String? revocationId,
    $core.String? oldUserUuid,
    $core.String? newUserUuid,
    $core.String? verifyingIspCnpj,
    $fixnum.Int64? replacedAtMs,
    $core.List<$core.int>? principalSignature,
  }) {
    final result = create();
    if (revocationId != null) result.revocationId = revocationId;
    if (oldUserUuid != null) result.oldUserUuid = oldUserUuid;
    if (newUserUuid != null) result.newUserUuid = newUserUuid;
    if (verifyingIspCnpj != null) result.verifyingIspCnpj = verifyingIspCnpj;
    if (replacedAtMs != null) result.replacedAtMs = replacedAtMs;
    if (principalSignature != null)
      result.principalSignature = principalSignature;
    return result;
  }

  IdentityReplacementNotice._();

  factory IdentityReplacementNotice.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory IdentityReplacementNotice.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'IdentityReplacementNotice',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'revocationId')
    ..aOS(2, _omitFieldNames ? '' : 'oldUserUuid')
    ..aOS(3, _omitFieldNames ? '' : 'newUserUuid')
    ..aOS(4, _omitFieldNames ? '' : 'verifyingIspCnpj')
    ..aInt64(5, _omitFieldNames ? '' : 'replacedAtMs')
    ..a<$core.List<$core.int>>(
        6, _omitFieldNames ? '' : 'principalSignature', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  IdentityReplacementNotice clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  IdentityReplacementNotice copyWith(
          void Function(IdentityReplacementNotice) updates) =>
      super.copyWith((message) => updates(message as IdentityReplacementNotice))
          as IdentityReplacementNotice;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static IdentityReplacementNotice create() => IdentityReplacementNotice._();
  @$core.override
  IdentityReplacementNotice createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static IdentityReplacementNotice getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<IdentityReplacementNotice>(create);
  static IdentityReplacementNotice? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get revocationId => $_getSZ(0);
  @$pb.TagNumber(1)
  set revocationId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasRevocationId() => $_has(0);
  @$pb.TagNumber(1)
  void clearRevocationId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get oldUserUuid => $_getSZ(1);
  @$pb.TagNumber(2)
  set oldUserUuid($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasOldUserUuid() => $_has(1);
  @$pb.TagNumber(2)
  void clearOldUserUuid() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get newUserUuid => $_getSZ(2);
  @$pb.TagNumber(3)
  set newUserUuid($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasNewUserUuid() => $_has(2);
  @$pb.TagNumber(3)
  void clearNewUserUuid() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get verifyingIspCnpj => $_getSZ(3);
  @$pb.TagNumber(4)
  set verifyingIspCnpj($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasVerifyingIspCnpj() => $_has(3);
  @$pb.TagNumber(4)
  void clearVerifyingIspCnpj() => $_clearField(4);

  @$pb.TagNumber(5)
  $fixnum.Int64 get replacedAtMs => $_getI64(4);
  @$pb.TagNumber(5)
  set replacedAtMs($fixnum.Int64 value) => $_setInt64(4, value);
  @$pb.TagNumber(5)
  $core.bool hasReplacedAtMs() => $_has(4);
  @$pb.TagNumber(5)
  void clearReplacedAtMs() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.List<$core.int> get principalSignature => $_getN(5);
  @$pb.TagNumber(6)
  set principalSignature($core.List<$core.int> value) => $_setBytes(5, value);
  @$pb.TagNumber(6)
  $core.bool hasPrincipalSignature() => $_has(5);
  @$pb.TagNumber(6)
  void clearPrincipalSignature() => $_clearField(6);
}

/// Evento monotono do feed de revogacoes (conta, colaborador ou dispositivo delegado)
class RevocationEvent extends $pb.GeneratedMessage {
  factory RevocationEvent({
    $core.String? revocationId,
    $fixnum.Int64? revocationSeq,
    $core.String? targetUuid,
    $core.String? revokedDeviceId,
    $core.String? reason,
    $fixnum.Int64? createdAtMs,
    $core.List<$core.int>? principalSignature,
    IdentityReplacementNotice? replacementNotice,
  }) {
    final result = create();
    if (revocationId != null) result.revocationId = revocationId;
    if (revocationSeq != null) result.revocationSeq = revocationSeq;
    if (targetUuid != null) result.targetUuid = targetUuid;
    if (revokedDeviceId != null) result.revokedDeviceId = revokedDeviceId;
    if (reason != null) result.reason = reason;
    if (createdAtMs != null) result.createdAtMs = createdAtMs;
    if (principalSignature != null)
      result.principalSignature = principalSignature;
    if (replacementNotice != null) result.replacementNotice = replacementNotice;
    return result;
  }

  RevocationEvent._();

  factory RevocationEvent.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory RevocationEvent.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'RevocationEvent',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'revocationId')
    ..aInt64(2, _omitFieldNames ? '' : 'revocationSeq')
    ..aOS(3, _omitFieldNames ? '' : 'targetUuid')
    ..aOS(4, _omitFieldNames ? '' : 'revokedDeviceId')
    ..aOS(5, _omitFieldNames ? '' : 'reason')
    ..aInt64(6, _omitFieldNames ? '' : 'createdAtMs')
    ..a<$core.List<$core.int>>(
        7, _omitFieldNames ? '' : 'principalSignature', $pb.PbFieldType.OY)
    ..aOM<IdentityReplacementNotice>(
        8, _omitFieldNames ? '' : 'replacementNotice',
        subBuilder: IdentityReplacementNotice.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RevocationEvent clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RevocationEvent copyWith(void Function(RevocationEvent) updates) =>
      super.copyWith((message) => updates(message as RevocationEvent))
          as RevocationEvent;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static RevocationEvent create() => RevocationEvent._();
  @$core.override
  RevocationEvent createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static RevocationEvent getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<RevocationEvent>(create);
  static RevocationEvent? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get revocationId => $_getSZ(0);
  @$pb.TagNumber(1)
  set revocationId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasRevocationId() => $_has(0);
  @$pb.TagNumber(1)
  void clearRevocationId() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get revocationSeq => $_getI64(1);
  @$pb.TagNumber(2)
  set revocationSeq($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasRevocationSeq() => $_has(1);
  @$pb.TagNumber(2)
  void clearRevocationSeq() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get targetUuid => $_getSZ(2);
  @$pb.TagNumber(3)
  set targetUuid($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasTargetUuid() => $_has(2);
  @$pb.TagNumber(3)
  void clearTargetUuid() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get revokedDeviceId => $_getSZ(3);
  @$pb.TagNumber(4)
  set revokedDeviceId($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasRevokedDeviceId() => $_has(3);
  @$pb.TagNumber(4)
  void clearRevokedDeviceId() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get reason => $_getSZ(4);
  @$pb.TagNumber(5)
  set reason($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasReason() => $_has(4);
  @$pb.TagNumber(5)
  void clearReason() => $_clearField(5);

  @$pb.TagNumber(6)
  $fixnum.Int64 get createdAtMs => $_getI64(5);
  @$pb.TagNumber(6)
  set createdAtMs($fixnum.Int64 value) => $_setInt64(5, value);
  @$pb.TagNumber(6)
  $core.bool hasCreatedAtMs() => $_has(5);
  @$pb.TagNumber(6)
  void clearCreatedAtMs() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.List<$core.int> get principalSignature => $_getN(6);
  @$pb.TagNumber(7)
  set principalSignature($core.List<$core.int> value) => $_setBytes(6, value);
  @$pb.TagNumber(7)
  $core.bool hasPrincipalSignature() => $_has(6);
  @$pb.TagNumber(7)
  void clearPrincipalSignature() => $_clearField(7);

  @$pb.TagNumber(8)
  IdentityReplacementNotice get replacementNotice => $_getN(7);
  @$pb.TagNumber(8)
  set replacementNotice(IdentityReplacementNotice value) =>
      $_setField(8, value);
  @$pb.TagNumber(8)
  $core.bool hasReplacementNotice() => $_has(7);
  @$pb.TagNumber(8)
  void clearReplacementNotice() => $_clearField(8);
  @$pb.TagNumber(8)
  IdentityReplacementNotice ensureReplacementNotice() => $_ensure(7);
}

/// Credencial de colaborador verificado assinada por um Socio Administrador do ISP
class CollaboratorAuthCert extends $pb.GeneratedMessage {
  factory CollaboratorAuthCert({
    $core.String? certId,
    $core.String? ispCnpj,
    $core.String? partnerUuid,
    $core.String? collaboratorUuid,
    $core.List<$core.int>? collabCpfSha256,
    $core.List<$core.int>? collabEd25519Pub,
    $fixnum.Int64? issuedAtMs,
    $fixnum.Int64? expiresAtMs,
    $core.List<$core.int>? partnerSignature,
  }) {
    final result = create();
    if (certId != null) result.certId = certId;
    if (ispCnpj != null) result.ispCnpj = ispCnpj;
    if (partnerUuid != null) result.partnerUuid = partnerUuid;
    if (collaboratorUuid != null) result.collaboratorUuid = collaboratorUuid;
    if (collabCpfSha256 != null) result.collabCpfSha256 = collabCpfSha256;
    if (collabEd25519Pub != null) result.collabEd25519Pub = collabEd25519Pub;
    if (issuedAtMs != null) result.issuedAtMs = issuedAtMs;
    if (expiresAtMs != null) result.expiresAtMs = expiresAtMs;
    if (partnerSignature != null) result.partnerSignature = partnerSignature;
    return result;
  }

  CollaboratorAuthCert._();

  factory CollaboratorAuthCert.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory CollaboratorAuthCert.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CollaboratorAuthCert',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'certId')
    ..aOS(2, _omitFieldNames ? '' : 'ispCnpj')
    ..aOS(3, _omitFieldNames ? '' : 'partnerUuid')
    ..aOS(4, _omitFieldNames ? '' : 'collaboratorUuid')
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'collabCpfSha256', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        6, _omitFieldNames ? '' : 'collabEd25519Pub', $pb.PbFieldType.OY)
    ..aInt64(7, _omitFieldNames ? '' : 'issuedAtMs')
    ..aInt64(8, _omitFieldNames ? '' : 'expiresAtMs')
    ..a<$core.List<$core.int>>(
        9, _omitFieldNames ? '' : 'partnerSignature', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CollaboratorAuthCert clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CollaboratorAuthCert copyWith(void Function(CollaboratorAuthCert) updates) =>
      super.copyWith((message) => updates(message as CollaboratorAuthCert))
          as CollaboratorAuthCert;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static CollaboratorAuthCert create() => CollaboratorAuthCert._();
  @$core.override
  CollaboratorAuthCert createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static CollaboratorAuthCert getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<CollaboratorAuthCert>(create);
  static CollaboratorAuthCert? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get certId => $_getSZ(0);
  @$pb.TagNumber(1)
  set certId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasCertId() => $_has(0);
  @$pb.TagNumber(1)
  void clearCertId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get ispCnpj => $_getSZ(1);
  @$pb.TagNumber(2)
  set ispCnpj($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasIspCnpj() => $_has(1);
  @$pb.TagNumber(2)
  void clearIspCnpj() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get partnerUuid => $_getSZ(2);
  @$pb.TagNumber(3)
  set partnerUuid($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasPartnerUuid() => $_has(2);
  @$pb.TagNumber(3)
  void clearPartnerUuid() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get collaboratorUuid => $_getSZ(3);
  @$pb.TagNumber(4)
  set collaboratorUuid($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasCollaboratorUuid() => $_has(3);
  @$pb.TagNumber(4)
  void clearCollaboratorUuid() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get collabCpfSha256 => $_getN(4);
  @$pb.TagNumber(5)
  set collabCpfSha256($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasCollabCpfSha256() => $_has(4);
  @$pb.TagNumber(5)
  void clearCollabCpfSha256() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.List<$core.int> get collabEd25519Pub => $_getN(5);
  @$pb.TagNumber(6)
  set collabEd25519Pub($core.List<$core.int> value) => $_setBytes(5, value);
  @$pb.TagNumber(6)
  $core.bool hasCollabEd25519Pub() => $_has(5);
  @$pb.TagNumber(6)
  void clearCollabEd25519Pub() => $_clearField(6);

  @$pb.TagNumber(7)
  $fixnum.Int64 get issuedAtMs => $_getI64(6);
  @$pb.TagNumber(7)
  set issuedAtMs($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(7)
  $core.bool hasIssuedAtMs() => $_has(6);
  @$pb.TagNumber(7)
  void clearIssuedAtMs() => $_clearField(7);

  @$pb.TagNumber(8)
  $fixnum.Int64 get expiresAtMs => $_getI64(7);
  @$pb.TagNumber(8)
  set expiresAtMs($fixnum.Int64 value) => $_setInt64(7, value);
  @$pb.TagNumber(8)
  $core.bool hasExpiresAtMs() => $_has(7);
  @$pb.TagNumber(8)
  void clearExpiresAtMs() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.List<$core.int> get partnerSignature => $_getN(8);
  @$pb.TagNumber(9)
  set partnerSignature($core.List<$core.int> value) => $_setBytes(8, value);
  @$pb.TagNumber(9)
  $core.bool hasPartnerSignature() => $_has(8);
  @$pb.TagNumber(9)
  void clearPartnerSignature() => $_clearField(9);
}

class RegisterSessionRequest extends $pb.GeneratedMessage {
  factory RegisterSessionRequest({
    $core.String? userUuid,
    $core.String? nodeAddress,
    $core.List<$core.int>? publicKey,
    $core.List<$core.int>? x25519PublicKey,
    $core.List<$core.int>? signature,
    $fixnum.Int64? timestamp,
    $core.String? deviceId,
    $core.List<$core.int>? rootEd25519Pub,
    DeviceDelegationCert? delegationCert,
    VerificationCredential? verificationCredential,
    HomeNodeClaim? homeNodeClaim,
  }) {
    final result = create();
    if (userUuid != null) result.userUuid = userUuid;
    if (nodeAddress != null) result.nodeAddress = nodeAddress;
    if (publicKey != null) result.publicKey = publicKey;
    if (x25519PublicKey != null) result.x25519PublicKey = x25519PublicKey;
    if (signature != null) result.signature = signature;
    if (timestamp != null) result.timestamp = timestamp;
    if (deviceId != null) result.deviceId = deviceId;
    if (rootEd25519Pub != null) result.rootEd25519Pub = rootEd25519Pub;
    if (delegationCert != null) result.delegationCert = delegationCert;
    if (verificationCredential != null)
      result.verificationCredential = verificationCredential;
    if (homeNodeClaim != null) result.homeNodeClaim = homeNodeClaim;
    return result;
  }

  RegisterSessionRequest._();

  factory RegisterSessionRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory RegisterSessionRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'RegisterSessionRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'userUuid')
    ..aOS(2, _omitFieldNames ? '' : 'nodeAddress')
    ..a<$core.List<$core.int>>(
        3, _omitFieldNames ? '' : 'publicKey', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        4, _omitFieldNames ? '' : 'x25519PublicKey', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'signature', $pb.PbFieldType.OY)
    ..aInt64(6, _omitFieldNames ? '' : 'timestamp')
    ..aOS(7, _omitFieldNames ? '' : 'deviceId')
    ..a<$core.List<$core.int>>(
        8, _omitFieldNames ? '' : 'rootEd25519Pub', $pb.PbFieldType.OY)
    ..aOM<DeviceDelegationCert>(9, _omitFieldNames ? '' : 'delegationCert',
        subBuilder: DeviceDelegationCert.create)
    ..aOM<VerificationCredential>(
        10, _omitFieldNames ? '' : 'verificationCredential',
        subBuilder: VerificationCredential.create)
    ..aOM<HomeNodeClaim>(11, _omitFieldNames ? '' : 'homeNodeClaim',
        subBuilder: HomeNodeClaim.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RegisterSessionRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RegisterSessionRequest copyWith(
          void Function(RegisterSessionRequest) updates) =>
      super.copyWith((message) => updates(message as RegisterSessionRequest))
          as RegisterSessionRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static RegisterSessionRequest create() => RegisterSessionRequest._();
  @$core.override
  RegisterSessionRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static RegisterSessionRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<RegisterSessionRequest>(create);
  static RegisterSessionRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get userUuid => $_getSZ(0);
  @$pb.TagNumber(1)
  set userUuid($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasUserUuid() => $_has(0);
  @$pb.TagNumber(1)
  void clearUserUuid() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get nodeAddress => $_getSZ(1);
  @$pb.TagNumber(2)
  set nodeAddress($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasNodeAddress() => $_has(1);
  @$pb.TagNumber(2)
  void clearNodeAddress() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.List<$core.int> get publicKey => $_getN(2);
  @$pb.TagNumber(3)
  set publicKey($core.List<$core.int> value) => $_setBytes(2, value);
  @$pb.TagNumber(3)
  $core.bool hasPublicKey() => $_has(2);
  @$pb.TagNumber(3)
  void clearPublicKey() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.List<$core.int> get x25519PublicKey => $_getN(3);
  @$pb.TagNumber(4)
  set x25519PublicKey($core.List<$core.int> value) => $_setBytes(3, value);
  @$pb.TagNumber(4)
  $core.bool hasX25519PublicKey() => $_has(3);
  @$pb.TagNumber(4)
  void clearX25519PublicKey() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get signature => $_getN(4);
  @$pb.TagNumber(5)
  set signature($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasSignature() => $_has(4);
  @$pb.TagNumber(5)
  void clearSignature() => $_clearField(5);

  @$pb.TagNumber(6)
  $fixnum.Int64 get timestamp => $_getI64(5);
  @$pb.TagNumber(6)
  set timestamp($fixnum.Int64 value) => $_setInt64(5, value);
  @$pb.TagNumber(6)
  $core.bool hasTimestamp() => $_has(5);
  @$pb.TagNumber(6)
  void clearTimestamp() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.String get deviceId => $_getSZ(6);
  @$pb.TagNumber(7)
  set deviceId($core.String value) => $_setString(6, value);
  @$pb.TagNumber(7)
  $core.bool hasDeviceId() => $_has(6);
  @$pb.TagNumber(7)
  void clearDeviceId() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.List<$core.int> get rootEd25519Pub => $_getN(7);
  @$pb.TagNumber(8)
  set rootEd25519Pub($core.List<$core.int> value) => $_setBytes(7, value);
  @$pb.TagNumber(8)
  $core.bool hasRootEd25519Pub() => $_has(7);
  @$pb.TagNumber(8)
  void clearRootEd25519Pub() => $_clearField(8);

  @$pb.TagNumber(9)
  DeviceDelegationCert get delegationCert => $_getN(8);
  @$pb.TagNumber(9)
  set delegationCert(DeviceDelegationCert value) => $_setField(9, value);
  @$pb.TagNumber(9)
  $core.bool hasDelegationCert() => $_has(8);
  @$pb.TagNumber(9)
  void clearDelegationCert() => $_clearField(9);
  @$pb.TagNumber(9)
  DeviceDelegationCert ensureDelegationCert() => $_ensure(8);

  @$pb.TagNumber(10)
  VerificationCredential get verificationCredential => $_getN(9);
  @$pb.TagNumber(10)
  set verificationCredential(VerificationCredential value) =>
      $_setField(10, value);
  @$pb.TagNumber(10)
  $core.bool hasVerificationCredential() => $_has(9);
  @$pb.TagNumber(10)
  void clearVerificationCredential() => $_clearField(10);
  @$pb.TagNumber(10)
  VerificationCredential ensureVerificationCredential() => $_ensure(9);

  @$pb.TagNumber(11)
  HomeNodeClaim get homeNodeClaim => $_getN(10);
  @$pb.TagNumber(11)
  set homeNodeClaim(HomeNodeClaim value) => $_setField(11, value);
  @$pb.TagNumber(11)
  $core.bool hasHomeNodeClaim() => $_has(10);
  @$pb.TagNumber(11)
  void clearHomeNodeClaim() => $_clearField(11);
  @$pb.TagNumber(11)
  HomeNodeClaim ensureHomeNodeClaim() => $_ensure(10);
}

class RegisterSessionResponse extends $pb.GeneratedMessage {
  factory RegisterSessionResponse({
    $core.bool? success,
    $core.String? errorMessage,
  }) {
    final result = create();
    if (success != null) result.success = success;
    if (errorMessage != null) result.errorMessage = errorMessage;
    return result;
  }

  RegisterSessionResponse._();

  factory RegisterSessionResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory RegisterSessionResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'RegisterSessionResponse',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOB(1, _omitFieldNames ? '' : 'success')
    ..aOS(2, _omitFieldNames ? '' : 'errorMessage')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RegisterSessionResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RegisterSessionResponse copyWith(
          void Function(RegisterSessionResponse) updates) =>
      super.copyWith((message) => updates(message as RegisterSessionResponse))
          as RegisterSessionResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static RegisterSessionResponse create() => RegisterSessionResponse._();
  @$core.override
  RegisterSessionResponse createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static RegisterSessionResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<RegisterSessionResponse>(create);
  static RegisterSessionResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get success => $_getBF(0);
  @$pb.TagNumber(1)
  set success($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasSuccess() => $_has(0);
  @$pb.TagNumber(1)
  void clearSuccess() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get errorMessage => $_getSZ(1);
  @$pb.TagNumber(2)
  set errorMessage($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasErrorMessage() => $_has(1);
  @$pb.TagNumber(2)
  void clearErrorMessage() => $_clearField(2);
}

class FindUserSessionsRequest extends $pb.GeneratedMessage {
  factory FindUserSessionsRequest({
    $core.String? targetUuid,
  }) {
    final result = create();
    if (targetUuid != null) result.targetUuid = targetUuid;
    return result;
  }

  FindUserSessionsRequest._();

  factory FindUserSessionsRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory FindUserSessionsRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'FindUserSessionsRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'targetUuid')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FindUserSessionsRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FindUserSessionsRequest copyWith(
          void Function(FindUserSessionsRequest) updates) =>
      super.copyWith((message) => updates(message as FindUserSessionsRequest))
          as FindUserSessionsRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static FindUserSessionsRequest create() => FindUserSessionsRequest._();
  @$core.override
  FindUserSessionsRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static FindUserSessionsRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<FindUserSessionsRequest>(create);
  static FindUserSessionsRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get targetUuid => $_getSZ(0);
  @$pb.TagNumber(1)
  set targetUuid($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasTargetUuid() => $_has(0);
  @$pb.TagNumber(1)
  void clearTargetUuid() => $_clearField(1);
}

class UserSessionInfo extends $pb.GeneratedMessage {
  factory UserSessionInfo({
    $core.String? nodeAddress,
    $core.List<$core.int>? ed25519PublicKey,
    $core.List<$core.int>? x25519PublicKey,
    $fixnum.Int64? timestamp,
    $core.String? deviceId,
    $core.List<$core.int>? rootEd25519Pub,
    $core.List<$core.int>? deviceEd25519Pub,
    $core.List<$core.int>? sessionSignature,
    DeviceDelegationCert? delegationCert,
    VerificationCredential? verificationCredential,
  }) {
    final result = create();
    if (nodeAddress != null) result.nodeAddress = nodeAddress;
    if (ed25519PublicKey != null) result.ed25519PublicKey = ed25519PublicKey;
    if (x25519PublicKey != null) result.x25519PublicKey = x25519PublicKey;
    if (timestamp != null) result.timestamp = timestamp;
    if (deviceId != null) result.deviceId = deviceId;
    if (rootEd25519Pub != null) result.rootEd25519Pub = rootEd25519Pub;
    if (deviceEd25519Pub != null) result.deviceEd25519Pub = deviceEd25519Pub;
    if (sessionSignature != null) result.sessionSignature = sessionSignature;
    if (delegationCert != null) result.delegationCert = delegationCert;
    if (verificationCredential != null)
      result.verificationCredential = verificationCredential;
    return result;
  }

  UserSessionInfo._();

  factory UserSessionInfo.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory UserSessionInfo.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'UserSessionInfo',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'nodeAddress')
    ..a<$core.List<$core.int>>(
        2, _omitFieldNames ? '' : 'ed25519PublicKey', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        3, _omitFieldNames ? '' : 'x25519PublicKey', $pb.PbFieldType.OY)
    ..aInt64(4, _omitFieldNames ? '' : 'timestamp')
    ..aOS(5, _omitFieldNames ? '' : 'deviceId')
    ..a<$core.List<$core.int>>(
        6, _omitFieldNames ? '' : 'rootEd25519Pub', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        7, _omitFieldNames ? '' : 'deviceEd25519Pub', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        8, _omitFieldNames ? '' : 'sessionSignature', $pb.PbFieldType.OY)
    ..aOM<DeviceDelegationCert>(9, _omitFieldNames ? '' : 'delegationCert',
        subBuilder: DeviceDelegationCert.create)
    ..aOM<VerificationCredential>(
        10, _omitFieldNames ? '' : 'verificationCredential',
        subBuilder: VerificationCredential.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  UserSessionInfo clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  UserSessionInfo copyWith(void Function(UserSessionInfo) updates) =>
      super.copyWith((message) => updates(message as UserSessionInfo))
          as UserSessionInfo;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static UserSessionInfo create() => UserSessionInfo._();
  @$core.override
  UserSessionInfo createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static UserSessionInfo getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<UserSessionInfo>(create);
  static UserSessionInfo? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get nodeAddress => $_getSZ(0);
  @$pb.TagNumber(1)
  set nodeAddress($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasNodeAddress() => $_has(0);
  @$pb.TagNumber(1)
  void clearNodeAddress() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.List<$core.int> get ed25519PublicKey => $_getN(1);
  @$pb.TagNumber(2)
  set ed25519PublicKey($core.List<$core.int> value) => $_setBytes(1, value);
  @$pb.TagNumber(2)
  $core.bool hasEd25519PublicKey() => $_has(1);
  @$pb.TagNumber(2)
  void clearEd25519PublicKey() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.List<$core.int> get x25519PublicKey => $_getN(2);
  @$pb.TagNumber(3)
  set x25519PublicKey($core.List<$core.int> value) => $_setBytes(2, value);
  @$pb.TagNumber(3)
  $core.bool hasX25519PublicKey() => $_has(2);
  @$pb.TagNumber(3)
  void clearX25519PublicKey() => $_clearField(3);

  @$pb.TagNumber(4)
  $fixnum.Int64 get timestamp => $_getI64(3);
  @$pb.TagNumber(4)
  set timestamp($fixnum.Int64 value) => $_setInt64(3, value);
  @$pb.TagNumber(4)
  $core.bool hasTimestamp() => $_has(3);
  @$pb.TagNumber(4)
  void clearTimestamp() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get deviceId => $_getSZ(4);
  @$pb.TagNumber(5)
  set deviceId($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasDeviceId() => $_has(4);
  @$pb.TagNumber(5)
  void clearDeviceId() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.List<$core.int> get rootEd25519Pub => $_getN(5);
  @$pb.TagNumber(6)
  set rootEd25519Pub($core.List<$core.int> value) => $_setBytes(5, value);
  @$pb.TagNumber(6)
  $core.bool hasRootEd25519Pub() => $_has(5);
  @$pb.TagNumber(6)
  void clearRootEd25519Pub() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.List<$core.int> get deviceEd25519Pub => $_getN(6);
  @$pb.TagNumber(7)
  set deviceEd25519Pub($core.List<$core.int> value) => $_setBytes(6, value);
  @$pb.TagNumber(7)
  $core.bool hasDeviceEd25519Pub() => $_has(6);
  @$pb.TagNumber(7)
  void clearDeviceEd25519Pub() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.List<$core.int> get sessionSignature => $_getN(7);
  @$pb.TagNumber(8)
  set sessionSignature($core.List<$core.int> value) => $_setBytes(7, value);
  @$pb.TagNumber(8)
  $core.bool hasSessionSignature() => $_has(7);
  @$pb.TagNumber(8)
  void clearSessionSignature() => $_clearField(8);

  @$pb.TagNumber(9)
  DeviceDelegationCert get delegationCert => $_getN(8);
  @$pb.TagNumber(9)
  set delegationCert(DeviceDelegationCert value) => $_setField(9, value);
  @$pb.TagNumber(9)
  $core.bool hasDelegationCert() => $_has(8);
  @$pb.TagNumber(9)
  void clearDelegationCert() => $_clearField(9);
  @$pb.TagNumber(9)
  DeviceDelegationCert ensureDelegationCert() => $_ensure(8);

  @$pb.TagNumber(10)
  VerificationCredential get verificationCredential => $_getN(9);
  @$pb.TagNumber(10)
  set verificationCredential(VerificationCredential value) =>
      $_setField(10, value);
  @$pb.TagNumber(10)
  $core.bool hasVerificationCredential() => $_has(9);
  @$pb.TagNumber(10)
  void clearVerificationCredential() => $_clearField(10);
  @$pb.TagNumber(10)
  VerificationCredential ensureVerificationCredential() => $_ensure(9);
}

class FindUserSessionsResponse extends $pb.GeneratedMessage {
  factory FindUserSessionsResponse({
    $core.Iterable<$core.String>? nodeAddresses,
    $core.Iterable<UserSessionInfo>? sessions,
    HomeNodeClaim? homeNodeClaim,
    IdentityReplacementNotice? replacementNotice,
  }) {
    final result = create();
    if (nodeAddresses != null) result.nodeAddresses.addAll(nodeAddresses);
    if (sessions != null) result.sessions.addAll(sessions);
    if (homeNodeClaim != null) result.homeNodeClaim = homeNodeClaim;
    if (replacementNotice != null) result.replacementNotice = replacementNotice;
    return result;
  }

  FindUserSessionsResponse._();

  factory FindUserSessionsResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory FindUserSessionsResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'FindUserSessionsResponse',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..pPS(1, _omitFieldNames ? '' : 'nodeAddresses')
    ..pPM<UserSessionInfo>(2, _omitFieldNames ? '' : 'sessions',
        subBuilder: UserSessionInfo.create)
    ..aOM<HomeNodeClaim>(3, _omitFieldNames ? '' : 'homeNodeClaim',
        subBuilder: HomeNodeClaim.create)
    ..aOM<IdentityReplacementNotice>(
        4, _omitFieldNames ? '' : 'replacementNotice',
        subBuilder: IdentityReplacementNotice.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FindUserSessionsResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FindUserSessionsResponse copyWith(
          void Function(FindUserSessionsResponse) updates) =>
      super.copyWith((message) => updates(message as FindUserSessionsResponse))
          as FindUserSessionsResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static FindUserSessionsResponse create() => FindUserSessionsResponse._();
  @$core.override
  FindUserSessionsResponse createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static FindUserSessionsResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<FindUserSessionsResponse>(create);
  static FindUserSessionsResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<$core.String> get nodeAddresses => $_getList(0);

  @$pb.TagNumber(2)
  $pb.PbList<UserSessionInfo> get sessions => $_getList(1);

  @$pb.TagNumber(3)
  HomeNodeClaim get homeNodeClaim => $_getN(2);
  @$pb.TagNumber(3)
  set homeNodeClaim(HomeNodeClaim value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasHomeNodeClaim() => $_has(2);
  @$pb.TagNumber(3)
  void clearHomeNodeClaim() => $_clearField(3);
  @$pb.TagNumber(3)
  HomeNodeClaim ensureHomeNodeClaim() => $_ensure(2);

  @$pb.TagNumber(4)
  IdentityReplacementNotice get replacementNotice => $_getN(3);
  @$pb.TagNumber(4)
  set replacementNotice(IdentityReplacementNotice value) =>
      $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasReplacementNotice() => $_has(3);
  @$pb.TagNumber(4)
  void clearReplacementNotice() => $_clearField(4);
  @$pb.TagNumber(4)
  IdentityReplacementNotice ensureReplacementNotice() => $_ensure(3);
}

class ClientEnvelope extends $pb.GeneratedMessage {
  factory ClientEnvelope({
    $core.String? messageId,
    $core.String? senderUuid,
    $core.String? receiverUuid,
    MessageType? type,
    $core.List<$core.int>? encryptedPayload,
    $core.List<$core.int>? senderPublicKey,
    $core.List<$core.int>? signature,
    $fixnum.Int64? timestamp,
    $core.String? senderDeviceId,
    DeviceDelegationCert? senderDelegationCert,
    VerificationCredential? senderVerificationCredential,
    $core.String? refMessageId,
  }) {
    final result = create();
    if (messageId != null) result.messageId = messageId;
    if (senderUuid != null) result.senderUuid = senderUuid;
    if (receiverUuid != null) result.receiverUuid = receiverUuid;
    if (type != null) result.type = type;
    if (encryptedPayload != null) result.encryptedPayload = encryptedPayload;
    if (senderPublicKey != null) result.senderPublicKey = senderPublicKey;
    if (signature != null) result.signature = signature;
    if (timestamp != null) result.timestamp = timestamp;
    if (senderDeviceId != null) result.senderDeviceId = senderDeviceId;
    if (senderDelegationCert != null)
      result.senderDelegationCert = senderDelegationCert;
    if (senderVerificationCredential != null)
      result.senderVerificationCredential = senderVerificationCredential;
    if (refMessageId != null) result.refMessageId = refMessageId;
    return result;
  }

  ClientEnvelope._();

  factory ClientEnvelope.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory ClientEnvelope.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ClientEnvelope',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'messageId')
    ..aOS(2, _omitFieldNames ? '' : 'senderUuid')
    ..aOS(3, _omitFieldNames ? '' : 'receiverUuid')
    ..aE<MessageType>(4, _omitFieldNames ? '' : 'type',
        enumValues: MessageType.values)
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'encryptedPayload', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        6, _omitFieldNames ? '' : 'senderPublicKey', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        7, _omitFieldNames ? '' : 'signature', $pb.PbFieldType.OY)
    ..aInt64(8, _omitFieldNames ? '' : 'timestamp')
    ..aOS(9, _omitFieldNames ? '' : 'senderDeviceId')
    ..aOM<DeviceDelegationCert>(
        10, _omitFieldNames ? '' : 'senderDelegationCert',
        subBuilder: DeviceDelegationCert.create)
    ..aOM<VerificationCredential>(
        11, _omitFieldNames ? '' : 'senderVerificationCredential',
        subBuilder: VerificationCredential.create)
    ..aOS(12, _omitFieldNames ? '' : 'refMessageId')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ClientEnvelope clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ClientEnvelope copyWith(void Function(ClientEnvelope) updates) =>
      super.copyWith((message) => updates(message as ClientEnvelope))
          as ClientEnvelope;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static ClientEnvelope create() => ClientEnvelope._();
  @$core.override
  ClientEnvelope createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static ClientEnvelope getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ClientEnvelope>(create);
  static ClientEnvelope? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get messageId => $_getSZ(0);
  @$pb.TagNumber(1)
  set messageId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasMessageId() => $_has(0);
  @$pb.TagNumber(1)
  void clearMessageId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get senderUuid => $_getSZ(1);
  @$pb.TagNumber(2)
  set senderUuid($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSenderUuid() => $_has(1);
  @$pb.TagNumber(2)
  void clearSenderUuid() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get receiverUuid => $_getSZ(2);
  @$pb.TagNumber(3)
  set receiverUuid($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasReceiverUuid() => $_has(2);
  @$pb.TagNumber(3)
  void clearReceiverUuid() => $_clearField(3);

  @$pb.TagNumber(4)
  MessageType get type => $_getN(3);
  @$pb.TagNumber(4)
  set type(MessageType value) => $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasType() => $_has(3);
  @$pb.TagNumber(4)
  void clearType() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get encryptedPayload => $_getN(4);
  @$pb.TagNumber(5)
  set encryptedPayload($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasEncryptedPayload() => $_has(4);
  @$pb.TagNumber(5)
  void clearEncryptedPayload() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.List<$core.int> get senderPublicKey => $_getN(5);
  @$pb.TagNumber(6)
  set senderPublicKey($core.List<$core.int> value) => $_setBytes(5, value);
  @$pb.TagNumber(6)
  $core.bool hasSenderPublicKey() => $_has(5);
  @$pb.TagNumber(6)
  void clearSenderPublicKey() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.List<$core.int> get signature => $_getN(6);
  @$pb.TagNumber(7)
  set signature($core.List<$core.int> value) => $_setBytes(6, value);
  @$pb.TagNumber(7)
  $core.bool hasSignature() => $_has(6);
  @$pb.TagNumber(7)
  void clearSignature() => $_clearField(7);

  @$pb.TagNumber(8)
  $fixnum.Int64 get timestamp => $_getI64(7);
  @$pb.TagNumber(8)
  set timestamp($fixnum.Int64 value) => $_setInt64(7, value);
  @$pb.TagNumber(8)
  $core.bool hasTimestamp() => $_has(7);
  @$pb.TagNumber(8)
  void clearTimestamp() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.String get senderDeviceId => $_getSZ(8);
  @$pb.TagNumber(9)
  set senderDeviceId($core.String value) => $_setString(8, value);
  @$pb.TagNumber(9)
  $core.bool hasSenderDeviceId() => $_has(8);
  @$pb.TagNumber(9)
  void clearSenderDeviceId() => $_clearField(9);

  @$pb.TagNumber(10)
  DeviceDelegationCert get senderDelegationCert => $_getN(9);
  @$pb.TagNumber(10)
  set senderDelegationCert(DeviceDelegationCert value) => $_setField(10, value);
  @$pb.TagNumber(10)
  $core.bool hasSenderDelegationCert() => $_has(9);
  @$pb.TagNumber(10)
  void clearSenderDelegationCert() => $_clearField(10);
  @$pb.TagNumber(10)
  DeviceDelegationCert ensureSenderDelegationCert() => $_ensure(9);

  @$pb.TagNumber(11)
  VerificationCredential get senderVerificationCredential => $_getN(10);
  @$pb.TagNumber(11)
  set senderVerificationCredential(VerificationCredential value) =>
      $_setField(11, value);
  @$pb.TagNumber(11)
  $core.bool hasSenderVerificationCredential() => $_has(10);
  @$pb.TagNumber(11)
  void clearSenderVerificationCredential() => $_clearField(11);
  @$pb.TagNumber(11)
  VerificationCredential ensureSenderVerificationCredential() => $_ensure(10);

  @$pb.TagNumber(12)
  $core.String get refMessageId => $_getSZ(11);
  @$pb.TagNumber(12)
  set refMessageId($core.String value) => $_setString(11, value);
  @$pb.TagNumber(12)
  $core.bool hasRefMessageId() => $_has(11);
  @$pb.TagNumber(12)
  void clearRefMessageId() => $_clearField(12);
}

class ServerEnvelope extends $pb.GeneratedMessage {
  factory ServerEnvelope({
    $core.String? messageId,
    $core.String? senderUuid,
    MessageType? type,
    $core.List<$core.int>? encryptedPayload,
    $core.List<$core.int>? senderPublicKey,
    $core.List<$core.int>? signature,
    $fixnum.Int64? timestamp,
    $core.String? receiverUuid,
    $core.String? senderDeviceId,
    DeviceDelegationCert? senderDelegationCert,
    VerificationCredential? senderVerificationCredential,
    $core.String? refMessageId,
    IdentityReplacementNotice? replacementNotice,
    RevocationEvent? revocationEvent,
  }) {
    final result = create();
    if (messageId != null) result.messageId = messageId;
    if (senderUuid != null) result.senderUuid = senderUuid;
    if (type != null) result.type = type;
    if (encryptedPayload != null) result.encryptedPayload = encryptedPayload;
    if (senderPublicKey != null) result.senderPublicKey = senderPublicKey;
    if (signature != null) result.signature = signature;
    if (timestamp != null) result.timestamp = timestamp;
    if (receiverUuid != null) result.receiverUuid = receiverUuid;
    if (senderDeviceId != null) result.senderDeviceId = senderDeviceId;
    if (senderDelegationCert != null)
      result.senderDelegationCert = senderDelegationCert;
    if (senderVerificationCredential != null)
      result.senderVerificationCredential = senderVerificationCredential;
    if (refMessageId != null) result.refMessageId = refMessageId;
    if (replacementNotice != null) result.replacementNotice = replacementNotice;
    if (revocationEvent != null) result.revocationEvent = revocationEvent;
    return result;
  }

  ServerEnvelope._();

  factory ServerEnvelope.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory ServerEnvelope.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ServerEnvelope',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'messageId')
    ..aOS(2, _omitFieldNames ? '' : 'senderUuid')
    ..aE<MessageType>(3, _omitFieldNames ? '' : 'type',
        enumValues: MessageType.values)
    ..a<$core.List<$core.int>>(
        4, _omitFieldNames ? '' : 'encryptedPayload', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'senderPublicKey', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        6, _omitFieldNames ? '' : 'signature', $pb.PbFieldType.OY)
    ..aInt64(7, _omitFieldNames ? '' : 'timestamp')
    ..aOS(8, _omitFieldNames ? '' : 'receiverUuid')
    ..aOS(9, _omitFieldNames ? '' : 'senderDeviceId')
    ..aOM<DeviceDelegationCert>(
        10, _omitFieldNames ? '' : 'senderDelegationCert',
        subBuilder: DeviceDelegationCert.create)
    ..aOM<VerificationCredential>(
        11, _omitFieldNames ? '' : 'senderVerificationCredential',
        subBuilder: VerificationCredential.create)
    ..aOS(12, _omitFieldNames ? '' : 'refMessageId')
    ..aOM<IdentityReplacementNotice>(
        13, _omitFieldNames ? '' : 'replacementNotice',
        subBuilder: IdentityReplacementNotice.create)
    ..aOM<RevocationEvent>(14, _omitFieldNames ? '' : 'revocationEvent',
        subBuilder: RevocationEvent.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ServerEnvelope clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ServerEnvelope copyWith(void Function(ServerEnvelope) updates) =>
      super.copyWith((message) => updates(message as ServerEnvelope))
          as ServerEnvelope;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static ServerEnvelope create() => ServerEnvelope._();
  @$core.override
  ServerEnvelope createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static ServerEnvelope getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ServerEnvelope>(create);
  static ServerEnvelope? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get messageId => $_getSZ(0);
  @$pb.TagNumber(1)
  set messageId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasMessageId() => $_has(0);
  @$pb.TagNumber(1)
  void clearMessageId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get senderUuid => $_getSZ(1);
  @$pb.TagNumber(2)
  set senderUuid($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSenderUuid() => $_has(1);
  @$pb.TagNumber(2)
  void clearSenderUuid() => $_clearField(2);

  @$pb.TagNumber(3)
  MessageType get type => $_getN(2);
  @$pb.TagNumber(3)
  set type(MessageType value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasType() => $_has(2);
  @$pb.TagNumber(3)
  void clearType() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.List<$core.int> get encryptedPayload => $_getN(3);
  @$pb.TagNumber(4)
  set encryptedPayload($core.List<$core.int> value) => $_setBytes(3, value);
  @$pb.TagNumber(4)
  $core.bool hasEncryptedPayload() => $_has(3);
  @$pb.TagNumber(4)
  void clearEncryptedPayload() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get senderPublicKey => $_getN(4);
  @$pb.TagNumber(5)
  set senderPublicKey($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasSenderPublicKey() => $_has(4);
  @$pb.TagNumber(5)
  void clearSenderPublicKey() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.List<$core.int> get signature => $_getN(5);
  @$pb.TagNumber(6)
  set signature($core.List<$core.int> value) => $_setBytes(5, value);
  @$pb.TagNumber(6)
  $core.bool hasSignature() => $_has(5);
  @$pb.TagNumber(6)
  void clearSignature() => $_clearField(6);

  @$pb.TagNumber(7)
  $fixnum.Int64 get timestamp => $_getI64(6);
  @$pb.TagNumber(7)
  set timestamp($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(7)
  $core.bool hasTimestamp() => $_has(6);
  @$pb.TagNumber(7)
  void clearTimestamp() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.String get receiverUuid => $_getSZ(7);
  @$pb.TagNumber(8)
  set receiverUuid($core.String value) => $_setString(7, value);
  @$pb.TagNumber(8)
  $core.bool hasReceiverUuid() => $_has(7);
  @$pb.TagNumber(8)
  void clearReceiverUuid() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.String get senderDeviceId => $_getSZ(8);
  @$pb.TagNumber(9)
  set senderDeviceId($core.String value) => $_setString(8, value);
  @$pb.TagNumber(9)
  $core.bool hasSenderDeviceId() => $_has(8);
  @$pb.TagNumber(9)
  void clearSenderDeviceId() => $_clearField(9);

  @$pb.TagNumber(10)
  DeviceDelegationCert get senderDelegationCert => $_getN(9);
  @$pb.TagNumber(10)
  set senderDelegationCert(DeviceDelegationCert value) => $_setField(10, value);
  @$pb.TagNumber(10)
  $core.bool hasSenderDelegationCert() => $_has(9);
  @$pb.TagNumber(10)
  void clearSenderDelegationCert() => $_clearField(10);
  @$pb.TagNumber(10)
  DeviceDelegationCert ensureSenderDelegationCert() => $_ensure(9);

  @$pb.TagNumber(11)
  VerificationCredential get senderVerificationCredential => $_getN(10);
  @$pb.TagNumber(11)
  set senderVerificationCredential(VerificationCredential value) =>
      $_setField(11, value);
  @$pb.TagNumber(11)
  $core.bool hasSenderVerificationCredential() => $_has(10);
  @$pb.TagNumber(11)
  void clearSenderVerificationCredential() => $_clearField(11);
  @$pb.TagNumber(11)
  VerificationCredential ensureSenderVerificationCredential() => $_ensure(10);

  @$pb.TagNumber(12)
  $core.String get refMessageId => $_getSZ(11);
  @$pb.TagNumber(12)
  set refMessageId($core.String value) => $_setString(11, value);
  @$pb.TagNumber(12)
  $core.bool hasRefMessageId() => $_has(11);
  @$pb.TagNumber(12)
  void clearRefMessageId() => $_clearField(12);

  @$pb.TagNumber(13)
  IdentityReplacementNotice get replacementNotice => $_getN(12);
  @$pb.TagNumber(13)
  set replacementNotice(IdentityReplacementNotice value) =>
      $_setField(13, value);
  @$pb.TagNumber(13)
  $core.bool hasReplacementNotice() => $_has(12);
  @$pb.TagNumber(13)
  void clearReplacementNotice() => $_clearField(13);
  @$pb.TagNumber(13)
  IdentityReplacementNotice ensureReplacementNotice() => $_ensure(12);

  @$pb.TagNumber(14)
  RevocationEvent get revocationEvent => $_getN(13);
  @$pb.TagNumber(14)
  set revocationEvent(RevocationEvent value) => $_setField(14, value);
  @$pb.TagNumber(14)
  $core.bool hasRevocationEvent() => $_has(13);
  @$pb.TagNumber(14)
  void clearRevocationEvent() => $_clearField(14);
  @$pb.TagNumber(14)
  RevocationEvent ensureRevocationEvent() => $_ensure(13);
}

class PingRequest extends $pb.GeneratedMessage {
  factory PingRequest({
    $core.String? senderNodeId,
    $core.String? senderAddress,
  }) {
    final result = create();
    if (senderNodeId != null) result.senderNodeId = senderNodeId;
    if (senderAddress != null) result.senderAddress = senderAddress;
    return result;
  }

  PingRequest._();

  factory PingRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory PingRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'PingRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'senderNodeId')
    ..aOS(2, _omitFieldNames ? '' : 'senderAddress')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PingRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PingRequest copyWith(void Function(PingRequest) updates) =>
      super.copyWith((message) => updates(message as PingRequest))
          as PingRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static PingRequest create() => PingRequest._();
  @$core.override
  PingRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static PingRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<PingRequest>(create);
  static PingRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get senderNodeId => $_getSZ(0);
  @$pb.TagNumber(1)
  set senderNodeId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasSenderNodeId() => $_has(0);
  @$pb.TagNumber(1)
  void clearSenderNodeId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get senderAddress => $_getSZ(1);
  @$pb.TagNumber(2)
  set senderAddress($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSenderAddress() => $_has(1);
  @$pb.TagNumber(2)
  void clearSenderAddress() => $_clearField(2);
}

class PingResponse extends $pb.GeneratedMessage {
  factory PingResponse({
    $core.String? responderNodeId,
  }) {
    final result = create();
    if (responderNodeId != null) result.responderNodeId = responderNodeId;
    return result;
  }

  PingResponse._();

  factory PingResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory PingResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'PingResponse',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'responderNodeId')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PingResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PingResponse copyWith(void Function(PingResponse) updates) =>
      super.copyWith((message) => updates(message as PingResponse))
          as PingResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static PingResponse create() => PingResponse._();
  @$core.override
  PingResponse createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static PingResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<PingResponse>(create);
  static PingResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get responderNodeId => $_getSZ(0);
  @$pb.TagNumber(1)
  set responderNodeId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasResponderNodeId() => $_has(0);
  @$pb.TagNumber(1)
  void clearResponderNodeId() => $_clearField(1);
}

class StoreRequest extends $pb.GeneratedMessage {
  factory StoreRequest({
    $core.String? userUuid,
    $core.String? nodeAddress,
    $core.List<$core.int>? publicKey,
    $core.List<$core.int>? x25519PublicKey,
    $core.List<$core.int>? signature,
    $fixnum.Int64? timestamp,
    $core.String? deviceId,
    $core.List<$core.int>? rootEd25519Pub,
    DeviceDelegationCert? delegationCert,
    VerificationCredential? verificationCredential,
    HomeNodeClaim? homeNodeClaim,
  }) {
    final result = create();
    if (userUuid != null) result.userUuid = userUuid;
    if (nodeAddress != null) result.nodeAddress = nodeAddress;
    if (publicKey != null) result.publicKey = publicKey;
    if (x25519PublicKey != null) result.x25519PublicKey = x25519PublicKey;
    if (signature != null) result.signature = signature;
    if (timestamp != null) result.timestamp = timestamp;
    if (deviceId != null) result.deviceId = deviceId;
    if (rootEd25519Pub != null) result.rootEd25519Pub = rootEd25519Pub;
    if (delegationCert != null) result.delegationCert = delegationCert;
    if (verificationCredential != null)
      result.verificationCredential = verificationCredential;
    if (homeNodeClaim != null) result.homeNodeClaim = homeNodeClaim;
    return result;
  }

  StoreRequest._();

  factory StoreRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory StoreRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'StoreRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'userUuid')
    ..aOS(2, _omitFieldNames ? '' : 'nodeAddress')
    ..a<$core.List<$core.int>>(
        3, _omitFieldNames ? '' : 'publicKey', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        4, _omitFieldNames ? '' : 'x25519PublicKey', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'signature', $pb.PbFieldType.OY)
    ..aInt64(6, _omitFieldNames ? '' : 'timestamp')
    ..aOS(7, _omitFieldNames ? '' : 'deviceId')
    ..a<$core.List<$core.int>>(
        8, _omitFieldNames ? '' : 'rootEd25519Pub', $pb.PbFieldType.OY)
    ..aOM<DeviceDelegationCert>(9, _omitFieldNames ? '' : 'delegationCert',
        subBuilder: DeviceDelegationCert.create)
    ..aOM<VerificationCredential>(
        10, _omitFieldNames ? '' : 'verificationCredential',
        subBuilder: VerificationCredential.create)
    ..aOM<HomeNodeClaim>(11, _omitFieldNames ? '' : 'homeNodeClaim',
        subBuilder: HomeNodeClaim.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StoreRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StoreRequest copyWith(void Function(StoreRequest) updates) =>
      super.copyWith((message) => updates(message as StoreRequest))
          as StoreRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static StoreRequest create() => StoreRequest._();
  @$core.override
  StoreRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static StoreRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<StoreRequest>(create);
  static StoreRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get userUuid => $_getSZ(0);
  @$pb.TagNumber(1)
  set userUuid($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasUserUuid() => $_has(0);
  @$pb.TagNumber(1)
  void clearUserUuid() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get nodeAddress => $_getSZ(1);
  @$pb.TagNumber(2)
  set nodeAddress($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasNodeAddress() => $_has(1);
  @$pb.TagNumber(2)
  void clearNodeAddress() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.List<$core.int> get publicKey => $_getN(2);
  @$pb.TagNumber(3)
  set publicKey($core.List<$core.int> value) => $_setBytes(2, value);
  @$pb.TagNumber(3)
  $core.bool hasPublicKey() => $_has(2);
  @$pb.TagNumber(3)
  void clearPublicKey() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.List<$core.int> get x25519PublicKey => $_getN(3);
  @$pb.TagNumber(4)
  set x25519PublicKey($core.List<$core.int> value) => $_setBytes(3, value);
  @$pb.TagNumber(4)
  $core.bool hasX25519PublicKey() => $_has(3);
  @$pb.TagNumber(4)
  void clearX25519PublicKey() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get signature => $_getN(4);
  @$pb.TagNumber(5)
  set signature($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasSignature() => $_has(4);
  @$pb.TagNumber(5)
  void clearSignature() => $_clearField(5);

  @$pb.TagNumber(6)
  $fixnum.Int64 get timestamp => $_getI64(5);
  @$pb.TagNumber(6)
  set timestamp($fixnum.Int64 value) => $_setInt64(5, value);
  @$pb.TagNumber(6)
  $core.bool hasTimestamp() => $_has(5);
  @$pb.TagNumber(6)
  void clearTimestamp() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.String get deviceId => $_getSZ(6);
  @$pb.TagNumber(7)
  set deviceId($core.String value) => $_setString(6, value);
  @$pb.TagNumber(7)
  $core.bool hasDeviceId() => $_has(6);
  @$pb.TagNumber(7)
  void clearDeviceId() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.List<$core.int> get rootEd25519Pub => $_getN(7);
  @$pb.TagNumber(8)
  set rootEd25519Pub($core.List<$core.int> value) => $_setBytes(7, value);
  @$pb.TagNumber(8)
  $core.bool hasRootEd25519Pub() => $_has(7);
  @$pb.TagNumber(8)
  void clearRootEd25519Pub() => $_clearField(8);

  @$pb.TagNumber(9)
  DeviceDelegationCert get delegationCert => $_getN(8);
  @$pb.TagNumber(9)
  set delegationCert(DeviceDelegationCert value) => $_setField(9, value);
  @$pb.TagNumber(9)
  $core.bool hasDelegationCert() => $_has(8);
  @$pb.TagNumber(9)
  void clearDelegationCert() => $_clearField(9);
  @$pb.TagNumber(9)
  DeviceDelegationCert ensureDelegationCert() => $_ensure(8);

  @$pb.TagNumber(10)
  VerificationCredential get verificationCredential => $_getN(9);
  @$pb.TagNumber(10)
  set verificationCredential(VerificationCredential value) =>
      $_setField(10, value);
  @$pb.TagNumber(10)
  $core.bool hasVerificationCredential() => $_has(9);
  @$pb.TagNumber(10)
  void clearVerificationCredential() => $_clearField(10);
  @$pb.TagNumber(10)
  VerificationCredential ensureVerificationCredential() => $_ensure(9);

  @$pb.TagNumber(11)
  HomeNodeClaim get homeNodeClaim => $_getN(10);
  @$pb.TagNumber(11)
  set homeNodeClaim(HomeNodeClaim value) => $_setField(11, value);
  @$pb.TagNumber(11)
  $core.bool hasHomeNodeClaim() => $_has(10);
  @$pb.TagNumber(11)
  void clearHomeNodeClaim() => $_clearField(11);
  @$pb.TagNumber(11)
  HomeNodeClaim ensureHomeNodeClaim() => $_ensure(10);
}

class StoreResponse extends $pb.GeneratedMessage {
  factory StoreResponse({
    $core.bool? success,
  }) {
    final result = create();
    if (success != null) result.success = success;
    return result;
  }

  StoreResponse._();

  factory StoreResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory StoreResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'StoreResponse',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOB(1, _omitFieldNames ? '' : 'success')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StoreResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StoreResponse copyWith(void Function(StoreResponse) updates) =>
      super.copyWith((message) => updates(message as StoreResponse))
          as StoreResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static StoreResponse create() => StoreResponse._();
  @$core.override
  StoreResponse createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static StoreResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<StoreResponse>(create);
  static StoreResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get success => $_getBF(0);
  @$pb.TagNumber(1)
  set success($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasSuccess() => $_has(0);
  @$pb.TagNumber(1)
  void clearSuccess() => $_clearField(1);
}

class NodeInfo extends $pb.GeneratedMessage {
  factory NodeInfo({
    $core.String? nodeId,
    $core.String? nodeAddress,
  }) {
    final result = create();
    if (nodeId != null) result.nodeId = nodeId;
    if (nodeAddress != null) result.nodeAddress = nodeAddress;
    return result;
  }

  NodeInfo._();

  factory NodeInfo.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory NodeInfo.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'NodeInfo',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'nodeId')
    ..aOS(2, _omitFieldNames ? '' : 'nodeAddress')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  NodeInfo clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  NodeInfo copyWith(void Function(NodeInfo) updates) =>
      super.copyWith((message) => updates(message as NodeInfo)) as NodeInfo;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static NodeInfo create() => NodeInfo._();
  @$core.override
  NodeInfo createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static NodeInfo getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<NodeInfo>(create);
  static NodeInfo? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get nodeId => $_getSZ(0);
  @$pb.TagNumber(1)
  set nodeId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasNodeId() => $_has(0);
  @$pb.TagNumber(1)
  void clearNodeId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get nodeAddress => $_getSZ(1);
  @$pb.TagNumber(2)
  set nodeAddress($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasNodeAddress() => $_has(1);
  @$pb.TagNumber(2)
  void clearNodeAddress() => $_clearField(2);
}

class FindNodeRequest extends $pb.GeneratedMessage {
  factory FindNodeRequest({
    $core.String? targetId,
  }) {
    final result = create();
    if (targetId != null) result.targetId = targetId;
    return result;
  }

  FindNodeRequest._();

  factory FindNodeRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory FindNodeRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'FindNodeRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'targetId')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FindNodeRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FindNodeRequest copyWith(void Function(FindNodeRequest) updates) =>
      super.copyWith((message) => updates(message as FindNodeRequest))
          as FindNodeRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static FindNodeRequest create() => FindNodeRequest._();
  @$core.override
  FindNodeRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static FindNodeRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<FindNodeRequest>(create);
  static FindNodeRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get targetId => $_getSZ(0);
  @$pb.TagNumber(1)
  set targetId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasTargetId() => $_has(0);
  @$pb.TagNumber(1)
  void clearTargetId() => $_clearField(1);
}

class FindNodeResponse extends $pb.GeneratedMessage {
  factory FindNodeResponse({
    $core.Iterable<NodeInfo>? closestNodes,
  }) {
    final result = create();
    if (closestNodes != null) result.closestNodes.addAll(closestNodes);
    return result;
  }

  FindNodeResponse._();

  factory FindNodeResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory FindNodeResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'FindNodeResponse',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..pPM<NodeInfo>(1, _omitFieldNames ? '' : 'closestNodes',
        subBuilder: NodeInfo.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FindNodeResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FindNodeResponse copyWith(void Function(FindNodeResponse) updates) =>
      super.copyWith((message) => updates(message as FindNodeResponse))
          as FindNodeResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static FindNodeResponse create() => FindNodeResponse._();
  @$core.override
  FindNodeResponse createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static FindNodeResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<FindNodeResponse>(create);
  static FindNodeResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<NodeInfo> get closestNodes => $_getList(0);
}

class FindValueRequest extends $pb.GeneratedMessage {
  factory FindValueRequest({
    $core.String? targetUuid,
  }) {
    final result = create();
    if (targetUuid != null) result.targetUuid = targetUuid;
    return result;
  }

  FindValueRequest._();

  factory FindValueRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory FindValueRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'FindValueRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'targetUuid')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FindValueRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FindValueRequest copyWith(void Function(FindValueRequest) updates) =>
      super.copyWith((message) => updates(message as FindValueRequest))
          as FindValueRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static FindValueRequest create() => FindValueRequest._();
  @$core.override
  FindValueRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static FindValueRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<FindValueRequest>(create);
  static FindValueRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get targetUuid => $_getSZ(0);
  @$pb.TagNumber(1)
  set targetUuid($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasTargetUuid() => $_has(0);
  @$pb.TagNumber(1)
  void clearTargetUuid() => $_clearField(1);
}

class FindValueResponse extends $pb.GeneratedMessage {
  factory FindValueResponse({
    $core.Iterable<UserSessionInfo>? sessions,
    $core.Iterable<NodeInfo>? closestNodes,
    HomeNodeClaim? homeNodeClaim,
    IdentityReplacementNotice? replacementNotice,
  }) {
    final result = create();
    if (sessions != null) result.sessions.addAll(sessions);
    if (closestNodes != null) result.closestNodes.addAll(closestNodes);
    if (homeNodeClaim != null) result.homeNodeClaim = homeNodeClaim;
    if (replacementNotice != null) result.replacementNotice = replacementNotice;
    return result;
  }

  FindValueResponse._();

  factory FindValueResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory FindValueResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'FindValueResponse',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..pPM<UserSessionInfo>(1, _omitFieldNames ? '' : 'sessions',
        subBuilder: UserSessionInfo.create)
    ..pPM<NodeInfo>(2, _omitFieldNames ? '' : 'closestNodes',
        subBuilder: NodeInfo.create)
    ..aOM<HomeNodeClaim>(3, _omitFieldNames ? '' : 'homeNodeClaim',
        subBuilder: HomeNodeClaim.create)
    ..aOM<IdentityReplacementNotice>(
        4, _omitFieldNames ? '' : 'replacementNotice',
        subBuilder: IdentityReplacementNotice.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FindValueResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FindValueResponse copyWith(void Function(FindValueResponse) updates) =>
      super.copyWith((message) => updates(message as FindValueResponse))
          as FindValueResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static FindValueResponse create() => FindValueResponse._();
  @$core.override
  FindValueResponse createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static FindValueResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<FindValueResponse>(create);
  static FindValueResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<UserSessionInfo> get sessions => $_getList(0);

  @$pb.TagNumber(2)
  $pb.PbList<NodeInfo> get closestNodes => $_getList(1);

  @$pb.TagNumber(3)
  HomeNodeClaim get homeNodeClaim => $_getN(2);
  @$pb.TagNumber(3)
  set homeNodeClaim(HomeNodeClaim value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasHomeNodeClaim() => $_has(2);
  @$pb.TagNumber(3)
  void clearHomeNodeClaim() => $_clearField(3);
  @$pb.TagNumber(3)
  HomeNodeClaim ensureHomeNodeClaim() => $_ensure(2);

  @$pb.TagNumber(4)
  IdentityReplacementNotice get replacementNotice => $_getN(3);
  @$pb.TagNumber(4)
  set replacementNotice(IdentityReplacementNotice value) =>
      $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasReplacementNotice() => $_has(3);
  @$pb.TagNumber(4)
  void clearReplacementNotice() => $_clearField(4);
  @$pb.TagNumber(4)
  IdentityReplacementNotice ensureReplacementNotice() => $_ensure(3);
}

class RoutePayloadRequest extends $pb.GeneratedMessage {
  factory RoutePayloadRequest({
    $core.String? messageId,
    $core.String? senderUuid,
    $core.String? receiverUuid,
    MessageType? type,
    $core.List<$core.int>? encryptedPayload,
    $core.List<$core.int>? senderPublicKey,
    $core.List<$core.int>? signature,
    $fixnum.Int64? timestamp,
    $core.String? senderDeviceId,
    DeviceDelegationCert? senderDelegationCert,
    VerificationCredential? senderVerificationCredential,
    $core.String? refMessageId,
    $core.int? hopCount,
  }) {
    final result = create();
    if (messageId != null) result.messageId = messageId;
    if (senderUuid != null) result.senderUuid = senderUuid;
    if (receiverUuid != null) result.receiverUuid = receiverUuid;
    if (type != null) result.type = type;
    if (encryptedPayload != null) result.encryptedPayload = encryptedPayload;
    if (senderPublicKey != null) result.senderPublicKey = senderPublicKey;
    if (signature != null) result.signature = signature;
    if (timestamp != null) result.timestamp = timestamp;
    if (senderDeviceId != null) result.senderDeviceId = senderDeviceId;
    if (senderDelegationCert != null)
      result.senderDelegationCert = senderDelegationCert;
    if (senderVerificationCredential != null)
      result.senderVerificationCredential = senderVerificationCredential;
    if (refMessageId != null) result.refMessageId = refMessageId;
    if (hopCount != null) result.hopCount = hopCount;
    return result;
  }

  RoutePayloadRequest._();

  factory RoutePayloadRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory RoutePayloadRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'RoutePayloadRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'messageId')
    ..aOS(2, _omitFieldNames ? '' : 'senderUuid')
    ..aOS(3, _omitFieldNames ? '' : 'receiverUuid')
    ..aE<MessageType>(4, _omitFieldNames ? '' : 'type',
        enumValues: MessageType.values)
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'encryptedPayload', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        6, _omitFieldNames ? '' : 'senderPublicKey', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        7, _omitFieldNames ? '' : 'signature', $pb.PbFieldType.OY)
    ..aInt64(8, _omitFieldNames ? '' : 'timestamp')
    ..aOS(9, _omitFieldNames ? '' : 'senderDeviceId')
    ..aOM<DeviceDelegationCert>(
        10, _omitFieldNames ? '' : 'senderDelegationCert',
        subBuilder: DeviceDelegationCert.create)
    ..aOM<VerificationCredential>(
        11, _omitFieldNames ? '' : 'senderVerificationCredential',
        subBuilder: VerificationCredential.create)
    ..aOS(12, _omitFieldNames ? '' : 'refMessageId')
    ..aI(13, _omitFieldNames ? '' : 'hopCount', fieldType: $pb.PbFieldType.OU3)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RoutePayloadRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RoutePayloadRequest copyWith(void Function(RoutePayloadRequest) updates) =>
      super.copyWith((message) => updates(message as RoutePayloadRequest))
          as RoutePayloadRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static RoutePayloadRequest create() => RoutePayloadRequest._();
  @$core.override
  RoutePayloadRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static RoutePayloadRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<RoutePayloadRequest>(create);
  static RoutePayloadRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get messageId => $_getSZ(0);
  @$pb.TagNumber(1)
  set messageId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasMessageId() => $_has(0);
  @$pb.TagNumber(1)
  void clearMessageId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get senderUuid => $_getSZ(1);
  @$pb.TagNumber(2)
  set senderUuid($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSenderUuid() => $_has(1);
  @$pb.TagNumber(2)
  void clearSenderUuid() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get receiverUuid => $_getSZ(2);
  @$pb.TagNumber(3)
  set receiverUuid($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasReceiverUuid() => $_has(2);
  @$pb.TagNumber(3)
  void clearReceiverUuid() => $_clearField(3);

  @$pb.TagNumber(4)
  MessageType get type => $_getN(3);
  @$pb.TagNumber(4)
  set type(MessageType value) => $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasType() => $_has(3);
  @$pb.TagNumber(4)
  void clearType() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get encryptedPayload => $_getN(4);
  @$pb.TagNumber(5)
  set encryptedPayload($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasEncryptedPayload() => $_has(4);
  @$pb.TagNumber(5)
  void clearEncryptedPayload() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.List<$core.int> get senderPublicKey => $_getN(5);
  @$pb.TagNumber(6)
  set senderPublicKey($core.List<$core.int> value) => $_setBytes(5, value);
  @$pb.TagNumber(6)
  $core.bool hasSenderPublicKey() => $_has(5);
  @$pb.TagNumber(6)
  void clearSenderPublicKey() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.List<$core.int> get signature => $_getN(6);
  @$pb.TagNumber(7)
  set signature($core.List<$core.int> value) => $_setBytes(6, value);
  @$pb.TagNumber(7)
  $core.bool hasSignature() => $_has(6);
  @$pb.TagNumber(7)
  void clearSignature() => $_clearField(7);

  @$pb.TagNumber(8)
  $fixnum.Int64 get timestamp => $_getI64(7);
  @$pb.TagNumber(8)
  set timestamp($fixnum.Int64 value) => $_setInt64(7, value);
  @$pb.TagNumber(8)
  $core.bool hasTimestamp() => $_has(7);
  @$pb.TagNumber(8)
  void clearTimestamp() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.String get senderDeviceId => $_getSZ(8);
  @$pb.TagNumber(9)
  set senderDeviceId($core.String value) => $_setString(8, value);
  @$pb.TagNumber(9)
  $core.bool hasSenderDeviceId() => $_has(8);
  @$pb.TagNumber(9)
  void clearSenderDeviceId() => $_clearField(9);

  @$pb.TagNumber(10)
  DeviceDelegationCert get senderDelegationCert => $_getN(9);
  @$pb.TagNumber(10)
  set senderDelegationCert(DeviceDelegationCert value) => $_setField(10, value);
  @$pb.TagNumber(10)
  $core.bool hasSenderDelegationCert() => $_has(9);
  @$pb.TagNumber(10)
  void clearSenderDelegationCert() => $_clearField(10);
  @$pb.TagNumber(10)
  DeviceDelegationCert ensureSenderDelegationCert() => $_ensure(9);

  @$pb.TagNumber(11)
  VerificationCredential get senderVerificationCredential => $_getN(10);
  @$pb.TagNumber(11)
  set senderVerificationCredential(VerificationCredential value) =>
      $_setField(11, value);
  @$pb.TagNumber(11)
  $core.bool hasSenderVerificationCredential() => $_has(10);
  @$pb.TagNumber(11)
  void clearSenderVerificationCredential() => $_clearField(11);
  @$pb.TagNumber(11)
  VerificationCredential ensureSenderVerificationCredential() => $_ensure(10);

  @$pb.TagNumber(12)
  $core.String get refMessageId => $_getSZ(11);
  @$pb.TagNumber(12)
  set refMessageId($core.String value) => $_setString(11, value);
  @$pb.TagNumber(12)
  $core.bool hasRefMessageId() => $_has(11);
  @$pb.TagNumber(12)
  void clearRefMessageId() => $_clearField(12);

  @$pb.TagNumber(13)
  $core.int get hopCount => $_getIZ(12);
  @$pb.TagNumber(13)
  set hopCount($core.int value) => $_setUnsignedInt32(12, value);
  @$pb.TagNumber(13)
  $core.bool hasHopCount() => $_has(12);
  @$pb.TagNumber(13)
  void clearHopCount() => $_clearField(13);
}

class RoutePayloadResponse extends $pb.GeneratedMessage {
  factory RoutePayloadResponse({
    $core.bool? delivered,
    DeliveryStatus? status,
    $core.Iterable<$core.String>? deliveredDeviceIds,
    $core.String? redirectHomeNode,
  }) {
    final result = create();
    if (delivered != null) result.delivered = delivered;
    if (status != null) result.status = status;
    if (deliveredDeviceIds != null)
      result.deliveredDeviceIds.addAll(deliveredDeviceIds);
    if (redirectHomeNode != null) result.redirectHomeNode = redirectHomeNode;
    return result;
  }

  RoutePayloadResponse._();

  factory RoutePayloadResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory RoutePayloadResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'RoutePayloadResponse',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOB(1, _omitFieldNames ? '' : 'delivered')
    ..aE<DeliveryStatus>(2, _omitFieldNames ? '' : 'status',
        enumValues: DeliveryStatus.values)
    ..pPS(3, _omitFieldNames ? '' : 'deliveredDeviceIds')
    ..aOS(4, _omitFieldNames ? '' : 'redirectHomeNode')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RoutePayloadResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RoutePayloadResponse copyWith(void Function(RoutePayloadResponse) updates) =>
      super.copyWith((message) => updates(message as RoutePayloadResponse))
          as RoutePayloadResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static RoutePayloadResponse create() => RoutePayloadResponse._();
  @$core.override
  RoutePayloadResponse createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static RoutePayloadResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<RoutePayloadResponse>(create);
  static RoutePayloadResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get delivered => $_getBF(0);
  @$pb.TagNumber(1)
  set delivered($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasDelivered() => $_has(0);
  @$pb.TagNumber(1)
  void clearDelivered() => $_clearField(1);

  @$pb.TagNumber(2)
  DeliveryStatus get status => $_getN(1);
  @$pb.TagNumber(2)
  set status(DeliveryStatus value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasStatus() => $_has(1);
  @$pb.TagNumber(2)
  void clearStatus() => $_clearField(2);

  @$pb.TagNumber(3)
  $pb.PbList<$core.String> get deliveredDeviceIds => $_getList(2);

  @$pb.TagNumber(4)
  $core.String get redirectHomeNode => $_getSZ(3);
  @$pb.TagNumber(4)
  set redirectHomeNode($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasRedirectHomeNode() => $_has(3);
  @$pb.TagNumber(4)
  void clearRedirectHomeNode() => $_clearField(4);
}

class TurnCredentialRequest extends $pb.GeneratedMessage {
  factory TurnCredentialRequest({
    $core.String? userUuid,
    $core.String? deviceId,
    $fixnum.Int64? timestampMs,
    $core.List<$core.int>? callerPublicKey,
    $core.List<$core.int>? signature,
    DeviceDelegationCert? delegationCert,
    VerificationCredential? verificationCredential,
  }) {
    final result = create();
    if (userUuid != null) result.userUuid = userUuid;
    if (deviceId != null) result.deviceId = deviceId;
    if (timestampMs != null) result.timestampMs = timestampMs;
    if (callerPublicKey != null) result.callerPublicKey = callerPublicKey;
    if (signature != null) result.signature = signature;
    if (delegationCert != null) result.delegationCert = delegationCert;
    if (verificationCredential != null)
      result.verificationCredential = verificationCredential;
    return result;
  }

  TurnCredentialRequest._();

  factory TurnCredentialRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory TurnCredentialRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TurnCredentialRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'userUuid')
    ..aOS(2, _omitFieldNames ? '' : 'deviceId')
    ..aInt64(3, _omitFieldNames ? '' : 'timestampMs')
    ..a<$core.List<$core.int>>(
        4, _omitFieldNames ? '' : 'callerPublicKey', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'signature', $pb.PbFieldType.OY)
    ..aOM<DeviceDelegationCert>(6, _omitFieldNames ? '' : 'delegationCert',
        subBuilder: DeviceDelegationCert.create)
    ..aOM<VerificationCredential>(
        7, _omitFieldNames ? '' : 'verificationCredential',
        subBuilder: VerificationCredential.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TurnCredentialRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TurnCredentialRequest copyWith(
          void Function(TurnCredentialRequest) updates) =>
      super.copyWith((message) => updates(message as TurnCredentialRequest))
          as TurnCredentialRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static TurnCredentialRequest create() => TurnCredentialRequest._();
  @$core.override
  TurnCredentialRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static TurnCredentialRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TurnCredentialRequest>(create);
  static TurnCredentialRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get userUuid => $_getSZ(0);
  @$pb.TagNumber(1)
  set userUuid($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasUserUuid() => $_has(0);
  @$pb.TagNumber(1)
  void clearUserUuid() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get deviceId => $_getSZ(1);
  @$pb.TagNumber(2)
  set deviceId($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasDeviceId() => $_has(1);
  @$pb.TagNumber(2)
  void clearDeviceId() => $_clearField(2);

  @$pb.TagNumber(3)
  $fixnum.Int64 get timestampMs => $_getI64(2);
  @$pb.TagNumber(3)
  set timestampMs($fixnum.Int64 value) => $_setInt64(2, value);
  @$pb.TagNumber(3)
  $core.bool hasTimestampMs() => $_has(2);
  @$pb.TagNumber(3)
  void clearTimestampMs() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.List<$core.int> get callerPublicKey => $_getN(3);
  @$pb.TagNumber(4)
  set callerPublicKey($core.List<$core.int> value) => $_setBytes(3, value);
  @$pb.TagNumber(4)
  $core.bool hasCallerPublicKey() => $_has(3);
  @$pb.TagNumber(4)
  void clearCallerPublicKey() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get signature => $_getN(4);
  @$pb.TagNumber(5)
  set signature($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasSignature() => $_has(4);
  @$pb.TagNumber(5)
  void clearSignature() => $_clearField(5);

  @$pb.TagNumber(6)
  DeviceDelegationCert get delegationCert => $_getN(5);
  @$pb.TagNumber(6)
  set delegationCert(DeviceDelegationCert value) => $_setField(6, value);
  @$pb.TagNumber(6)
  $core.bool hasDelegationCert() => $_has(5);
  @$pb.TagNumber(6)
  void clearDelegationCert() => $_clearField(6);
  @$pb.TagNumber(6)
  DeviceDelegationCert ensureDelegationCert() => $_ensure(5);

  @$pb.TagNumber(7)
  VerificationCredential get verificationCredential => $_getN(6);
  @$pb.TagNumber(7)
  set verificationCredential(VerificationCredential value) =>
      $_setField(7, value);
  @$pb.TagNumber(7)
  $core.bool hasVerificationCredential() => $_has(6);
  @$pb.TagNumber(7)
  void clearVerificationCredential() => $_clearField(7);
  @$pb.TagNumber(7)
  VerificationCredential ensureVerificationCredential() => $_ensure(6);
}

class TurnCredentialResponse extends $pb.GeneratedMessage {
  factory TurnCredentialResponse({
    $core.Iterable<$core.String>? uris,
    $core.String? username,
    $core.String? credential,
    $core.int? ttlSeconds,
    $core.int? maxBps,
  }) {
    final result = create();
    if (uris != null) result.uris.addAll(uris);
    if (username != null) result.username = username;
    if (credential != null) result.credential = credential;
    if (ttlSeconds != null) result.ttlSeconds = ttlSeconds;
    if (maxBps != null) result.maxBps = maxBps;
    return result;
  }

  TurnCredentialResponse._();

  factory TurnCredentialResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory TurnCredentialResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TurnCredentialResponse',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..pPS(1, _omitFieldNames ? '' : 'uris')
    ..aOS(2, _omitFieldNames ? '' : 'username')
    ..aOS(3, _omitFieldNames ? '' : 'credential')
    ..aI(4, _omitFieldNames ? '' : 'ttlSeconds')
    ..aI(5, _omitFieldNames ? '' : 'maxBps')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TurnCredentialResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TurnCredentialResponse copyWith(
          void Function(TurnCredentialResponse) updates) =>
      super.copyWith((message) => updates(message as TurnCredentialResponse))
          as TurnCredentialResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static TurnCredentialResponse create() => TurnCredentialResponse._();
  @$core.override
  TurnCredentialResponse createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static TurnCredentialResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TurnCredentialResponse>(create);
  static TurnCredentialResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<$core.String> get uris => $_getList(0);

  @$pb.TagNumber(2)
  $core.String get username => $_getSZ(1);
  @$pb.TagNumber(2)
  set username($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasUsername() => $_has(1);
  @$pb.TagNumber(2)
  void clearUsername() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get credential => $_getSZ(2);
  @$pb.TagNumber(3)
  set credential($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasCredential() => $_has(2);
  @$pb.TagNumber(3)
  void clearCredential() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get ttlSeconds => $_getIZ(3);
  @$pb.TagNumber(4)
  set ttlSeconds($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasTtlSeconds() => $_has(3);
  @$pb.TagNumber(4)
  void clearTtlSeconds() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.int get maxBps => $_getIZ(4);
  @$pb.TagNumber(5)
  set maxBps($core.int value) => $_setSignedInt32(4, value);
  @$pb.TagNumber(5)
  $core.bool hasMaxBps() => $_has(4);
  @$pb.TagNumber(5)
  void clearMaxBps() => $_clearField(5);
}

class SearchCnpjRequest extends $pb.GeneratedMessage {
  factory SearchCnpjRequest({
    $core.String? query,
    $core.int? limit,
  }) {
    final result = create();
    if (query != null) result.query = query;
    if (limit != null) result.limit = limit;
    return result;
  }

  SearchCnpjRequest._();

  factory SearchCnpjRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory SearchCnpjRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SearchCnpjRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'query')
    ..aI(2, _omitFieldNames ? '' : 'limit')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SearchCnpjRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SearchCnpjRequest copyWith(void Function(SearchCnpjRequest) updates) =>
      super.copyWith((message) => updates(message as SearchCnpjRequest))
          as SearchCnpjRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static SearchCnpjRequest create() => SearchCnpjRequest._();
  @$core.override
  SearchCnpjRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static SearchCnpjRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SearchCnpjRequest>(create);
  static SearchCnpjRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get query => $_getSZ(0);
  @$pb.TagNumber(1)
  set query($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasQuery() => $_has(0);
  @$pb.TagNumber(1)
  void clearQuery() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.int get limit => $_getIZ(1);
  @$pb.TagNumber(2)
  set limit($core.int value) => $_setSignedInt32(1, value);
  @$pb.TagNumber(2)
  $core.bool hasLimit() => $_has(1);
  @$pb.TagNumber(2)
  void clearLimit() => $_clearField(2);
}

class VerifiedCorporateEntry extends $pb.GeneratedMessage {
  factory VerifiedCorporateEntry({
    $core.String? corporateUuid,
    $core.String? cnpj,
    $core.String? displayName,
    $core.String? homeNodeAddress,
    $core.List<$core.int>? rootEd25519Pub,
    $core.List<$core.int>? rootX25519Pub,
    VerificationCredential? verificationCredential,
  }) {
    final result = create();
    if (corporateUuid != null) result.corporateUuid = corporateUuid;
    if (cnpj != null) result.cnpj = cnpj;
    if (displayName != null) result.displayName = displayName;
    if (homeNodeAddress != null) result.homeNodeAddress = homeNodeAddress;
    if (rootEd25519Pub != null) result.rootEd25519Pub = rootEd25519Pub;
    if (rootX25519Pub != null) result.rootX25519Pub = rootX25519Pub;
    if (verificationCredential != null)
      result.verificationCredential = verificationCredential;
    return result;
  }

  VerifiedCorporateEntry._();

  factory VerifiedCorporateEntry.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory VerifiedCorporateEntry.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'VerifiedCorporateEntry',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'corporateUuid')
    ..aOS(2, _omitFieldNames ? '' : 'cnpj')
    ..aOS(3, _omitFieldNames ? '' : 'displayName')
    ..aOS(4, _omitFieldNames ? '' : 'homeNodeAddress')
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'rootEd25519Pub', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        6, _omitFieldNames ? '' : 'rootX25519Pub', $pb.PbFieldType.OY)
    ..aOM<VerificationCredential>(
        7, _omitFieldNames ? '' : 'verificationCredential',
        subBuilder: VerificationCredential.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  VerifiedCorporateEntry clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  VerifiedCorporateEntry copyWith(
          void Function(VerifiedCorporateEntry) updates) =>
      super.copyWith((message) => updates(message as VerifiedCorporateEntry))
          as VerifiedCorporateEntry;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static VerifiedCorporateEntry create() => VerifiedCorporateEntry._();
  @$core.override
  VerifiedCorporateEntry createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static VerifiedCorporateEntry getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<VerifiedCorporateEntry>(create);
  static VerifiedCorporateEntry? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get corporateUuid => $_getSZ(0);
  @$pb.TagNumber(1)
  set corporateUuid($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasCorporateUuid() => $_has(0);
  @$pb.TagNumber(1)
  void clearCorporateUuid() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get cnpj => $_getSZ(1);
  @$pb.TagNumber(2)
  set cnpj($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasCnpj() => $_has(1);
  @$pb.TagNumber(2)
  void clearCnpj() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get displayName => $_getSZ(2);
  @$pb.TagNumber(3)
  set displayName($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasDisplayName() => $_has(2);
  @$pb.TagNumber(3)
  void clearDisplayName() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get homeNodeAddress => $_getSZ(3);
  @$pb.TagNumber(4)
  set homeNodeAddress($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasHomeNodeAddress() => $_has(3);
  @$pb.TagNumber(4)
  void clearHomeNodeAddress() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get rootEd25519Pub => $_getN(4);
  @$pb.TagNumber(5)
  set rootEd25519Pub($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasRootEd25519Pub() => $_has(4);
  @$pb.TagNumber(5)
  void clearRootEd25519Pub() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.List<$core.int> get rootX25519Pub => $_getN(5);
  @$pb.TagNumber(6)
  set rootX25519Pub($core.List<$core.int> value) => $_setBytes(5, value);
  @$pb.TagNumber(6)
  $core.bool hasRootX25519Pub() => $_has(5);
  @$pb.TagNumber(6)
  void clearRootX25519Pub() => $_clearField(6);

  @$pb.TagNumber(7)
  VerificationCredential get verificationCredential => $_getN(6);
  @$pb.TagNumber(7)
  set verificationCredential(VerificationCredential value) =>
      $_setField(7, value);
  @$pb.TagNumber(7)
  $core.bool hasVerificationCredential() => $_has(6);
  @$pb.TagNumber(7)
  void clearVerificationCredential() => $_clearField(7);
  @$pb.TagNumber(7)
  VerificationCredential ensureVerificationCredential() => $_ensure(6);
}

class SearchCnpjResponse extends $pb.GeneratedMessage {
  factory SearchCnpjResponse({
    $core.Iterable<VerifiedCorporateEntry>? entries,
  }) {
    final result = create();
    if (entries != null) result.entries.addAll(entries);
    return result;
  }

  SearchCnpjResponse._();

  factory SearchCnpjResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory SearchCnpjResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SearchCnpjResponse',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..pPM<VerifiedCorporateEntry>(1, _omitFieldNames ? '' : 'entries',
        subBuilder: VerifiedCorporateEntry.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SearchCnpjResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SearchCnpjResponse copyWith(void Function(SearchCnpjResponse) updates) =>
      super.copyWith((message) => updates(message as SearchCnpjResponse))
          as SearchCnpjResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static SearchCnpjResponse create() => SearchCnpjResponse._();
  @$core.override
  SearchCnpjResponse createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static SearchCnpjResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SearchCnpjResponse>(create);
  static SearchCnpjResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<VerifiedCorporateEntry> get entries => $_getList(0);
}

class PartnerSeedEntry extends $pb.GeneratedMessage {
  factory PartnerSeedEntry({
    $core.String? partnerUuid,
    $core.String? partnerCpf,
    $core.List<$core.int>? partnerEd25519Pub,
    $core.List<$core.int>? partnerX25519Pub,
  }) {
    final result = create();
    if (partnerUuid != null) result.partnerUuid = partnerUuid;
    if (partnerCpf != null) result.partnerCpf = partnerCpf;
    if (partnerEd25519Pub != null) result.partnerEd25519Pub = partnerEd25519Pub;
    if (partnerX25519Pub != null) result.partnerX25519Pub = partnerX25519Pub;
    return result;
  }

  PartnerSeedEntry._();

  factory PartnerSeedEntry.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory PartnerSeedEntry.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'PartnerSeedEntry',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'partnerUuid')
    ..aOS(2, _omitFieldNames ? '' : 'partnerCpf')
    ..a<$core.List<$core.int>>(
        3, _omitFieldNames ? '' : 'partnerEd25519Pub', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        4, _omitFieldNames ? '' : 'partnerX25519Pub', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PartnerSeedEntry clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PartnerSeedEntry copyWith(void Function(PartnerSeedEntry) updates) =>
      super.copyWith((message) => updates(message as PartnerSeedEntry))
          as PartnerSeedEntry;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static PartnerSeedEntry create() => PartnerSeedEntry._();
  @$core.override
  PartnerSeedEntry createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static PartnerSeedEntry getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<PartnerSeedEntry>(create);
  static PartnerSeedEntry? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get partnerUuid => $_getSZ(0);
  @$pb.TagNumber(1)
  set partnerUuid($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasPartnerUuid() => $_has(0);
  @$pb.TagNumber(1)
  void clearPartnerUuid() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get partnerCpf => $_getSZ(1);
  @$pb.TagNumber(2)
  set partnerCpf($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasPartnerCpf() => $_has(1);
  @$pb.TagNumber(2)
  void clearPartnerCpf() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.List<$core.int> get partnerEd25519Pub => $_getN(2);
  @$pb.TagNumber(3)
  set partnerEd25519Pub($core.List<$core.int> value) => $_setBytes(2, value);
  @$pb.TagNumber(3)
  $core.bool hasPartnerEd25519Pub() => $_has(2);
  @$pb.TagNumber(3)
  void clearPartnerEd25519Pub() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.List<$core.int> get partnerX25519Pub => $_getN(3);
  @$pb.TagNumber(4)
  set partnerX25519Pub($core.List<$core.int> value) => $_setBytes(3, value);
  @$pb.TagNumber(4)
  $core.bool hasPartnerX25519Pub() => $_has(3);
  @$pb.TagNumber(4)
  void clearPartnerX25519Pub() => $_clearField(4);
}

class RegisterPartnerIspRequest extends $pb.GeneratedMessage {
  factory RegisterPartnerIspRequest({
    $core.String? ispCnpj,
    $core.String? tradeName,
    $core.String? corporateUuid,
    $core.List<$core.int>? corpRootEd25519Pub,
    $core.List<$core.int>? corpRootX25519Pub,
    $core.String? nodeAddress,
    $core.Iterable<PartnerSeedEntry>? managingPartners,
    $fixnum.Int64? issuedAtMs,
    $core.List<$core.int>? rootSignature,
  }) {
    final result = create();
    if (ispCnpj != null) result.ispCnpj = ispCnpj;
    if (tradeName != null) result.tradeName = tradeName;
    if (corporateUuid != null) result.corporateUuid = corporateUuid;
    if (corpRootEd25519Pub != null)
      result.corpRootEd25519Pub = corpRootEd25519Pub;
    if (corpRootX25519Pub != null) result.corpRootX25519Pub = corpRootX25519Pub;
    if (nodeAddress != null) result.nodeAddress = nodeAddress;
    if (managingPartners != null)
      result.managingPartners.addAll(managingPartners);
    if (issuedAtMs != null) result.issuedAtMs = issuedAtMs;
    if (rootSignature != null) result.rootSignature = rootSignature;
    return result;
  }

  RegisterPartnerIspRequest._();

  factory RegisterPartnerIspRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory RegisterPartnerIspRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'RegisterPartnerIspRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'ispCnpj')
    ..aOS(2, _omitFieldNames ? '' : 'tradeName')
    ..aOS(3, _omitFieldNames ? '' : 'corporateUuid')
    ..a<$core.List<$core.int>>(
        4, _omitFieldNames ? '' : 'corpRootEd25519Pub', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'corpRootX25519Pub', $pb.PbFieldType.OY)
    ..aOS(6, _omitFieldNames ? '' : 'nodeAddress')
    ..pPM<PartnerSeedEntry>(7, _omitFieldNames ? '' : 'managingPartners',
        subBuilder: PartnerSeedEntry.create)
    ..aInt64(8, _omitFieldNames ? '' : 'issuedAtMs')
    ..a<$core.List<$core.int>>(
        9, _omitFieldNames ? '' : 'rootSignature', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RegisterPartnerIspRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RegisterPartnerIspRequest copyWith(
          void Function(RegisterPartnerIspRequest) updates) =>
      super.copyWith((message) => updates(message as RegisterPartnerIspRequest))
          as RegisterPartnerIspRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static RegisterPartnerIspRequest create() => RegisterPartnerIspRequest._();
  @$core.override
  RegisterPartnerIspRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static RegisterPartnerIspRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<RegisterPartnerIspRequest>(create);
  static RegisterPartnerIspRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get ispCnpj => $_getSZ(0);
  @$pb.TagNumber(1)
  set ispCnpj($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasIspCnpj() => $_has(0);
  @$pb.TagNumber(1)
  void clearIspCnpj() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get tradeName => $_getSZ(1);
  @$pb.TagNumber(2)
  set tradeName($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasTradeName() => $_has(1);
  @$pb.TagNumber(2)
  void clearTradeName() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get corporateUuid => $_getSZ(2);
  @$pb.TagNumber(3)
  set corporateUuid($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasCorporateUuid() => $_has(2);
  @$pb.TagNumber(3)
  void clearCorporateUuid() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.List<$core.int> get corpRootEd25519Pub => $_getN(3);
  @$pb.TagNumber(4)
  set corpRootEd25519Pub($core.List<$core.int> value) => $_setBytes(3, value);
  @$pb.TagNumber(4)
  $core.bool hasCorpRootEd25519Pub() => $_has(3);
  @$pb.TagNumber(4)
  void clearCorpRootEd25519Pub() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get corpRootX25519Pub => $_getN(4);
  @$pb.TagNumber(5)
  set corpRootX25519Pub($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasCorpRootX25519Pub() => $_has(4);
  @$pb.TagNumber(5)
  void clearCorpRootX25519Pub() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get nodeAddress => $_getSZ(5);
  @$pb.TagNumber(6)
  set nodeAddress($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasNodeAddress() => $_has(5);
  @$pb.TagNumber(6)
  void clearNodeAddress() => $_clearField(6);

  @$pb.TagNumber(7)
  $pb.PbList<PartnerSeedEntry> get managingPartners => $_getList(6);

  @$pb.TagNumber(8)
  $fixnum.Int64 get issuedAtMs => $_getI64(7);
  @$pb.TagNumber(8)
  set issuedAtMs($fixnum.Int64 value) => $_setInt64(7, value);
  @$pb.TagNumber(8)
  $core.bool hasIssuedAtMs() => $_has(7);
  @$pb.TagNumber(8)
  void clearIssuedAtMs() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.List<$core.int> get rootSignature => $_getN(8);
  @$pb.TagNumber(9)
  set rootSignature($core.List<$core.int> value) => $_setBytes(8, value);
  @$pb.TagNumber(9)
  $core.bool hasRootSignature() => $_has(8);
  @$pb.TagNumber(9)
  void clearRootSignature() => $_clearField(9);
}

class VerificationBundle extends $pb.GeneratedMessage {
  factory VerificationBundle({
    VerificationCredential? credential,
    HomeNodeClaim? homeNodeClaim,
    IdentityReplacementNotice? replacementNotice,
  }) {
    final result = create();
    if (credential != null) result.credential = credential;
    if (homeNodeClaim != null) result.homeNodeClaim = homeNodeClaim;
    if (replacementNotice != null) result.replacementNotice = replacementNotice;
    return result;
  }

  VerificationBundle._();

  factory VerificationBundle.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory VerificationBundle.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'VerificationBundle',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOM<VerificationCredential>(1, _omitFieldNames ? '' : 'credential',
        subBuilder: VerificationCredential.create)
    ..aOM<HomeNodeClaim>(2, _omitFieldNames ? '' : 'homeNodeClaim',
        subBuilder: HomeNodeClaim.create)
    ..aOM<IdentityReplacementNotice>(
        3, _omitFieldNames ? '' : 'replacementNotice',
        subBuilder: IdentityReplacementNotice.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  VerificationBundle clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  VerificationBundle copyWith(void Function(VerificationBundle) updates) =>
      super.copyWith((message) => updates(message as VerificationBundle))
          as VerificationBundle;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static VerificationBundle create() => VerificationBundle._();
  @$core.override
  VerificationBundle createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static VerificationBundle getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<VerificationBundle>(create);
  static VerificationBundle? _defaultInstance;

  @$pb.TagNumber(1)
  VerificationCredential get credential => $_getN(0);
  @$pb.TagNumber(1)
  set credential(VerificationCredential value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasCredential() => $_has(0);
  @$pb.TagNumber(1)
  void clearCredential() => $_clearField(1);
  @$pb.TagNumber(1)
  VerificationCredential ensureCredential() => $_ensure(0);

  @$pb.TagNumber(2)
  HomeNodeClaim get homeNodeClaim => $_getN(1);
  @$pb.TagNumber(2)
  set homeNodeClaim(HomeNodeClaim value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasHomeNodeClaim() => $_has(1);
  @$pb.TagNumber(2)
  void clearHomeNodeClaim() => $_clearField(2);
  @$pb.TagNumber(2)
  HomeNodeClaim ensureHomeNodeClaim() => $_ensure(1);

  @$pb.TagNumber(3)
  IdentityReplacementNotice get replacementNotice => $_getN(2);
  @$pb.TagNumber(3)
  set replacementNotice(IdentityReplacementNotice value) =>
      $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasReplacementNotice() => $_has(2);
  @$pb.TagNumber(3)
  void clearReplacementNotice() => $_clearField(3);
  @$pb.TagNumber(3)
  IdentityReplacementNotice ensureReplacementNotice() => $_ensure(2);
}

class GovernanceAck extends $pb.GeneratedMessage {
  factory GovernanceAck({
    $core.bool? accepted,
    $core.String? message,
    VerificationBundle? corporateBundle,
  }) {
    final result = create();
    if (accepted != null) result.accepted = accepted;
    if (message != null) result.message = message;
    if (corporateBundle != null) result.corporateBundle = corporateBundle;
    return result;
  }

  GovernanceAck._();

  factory GovernanceAck.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory GovernanceAck.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'GovernanceAck',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOB(1, _omitFieldNames ? '' : 'accepted')
    ..aOS(2, _omitFieldNames ? '' : 'message')
    ..aOM<VerificationBundle>(3, _omitFieldNames ? '' : 'corporateBundle',
        subBuilder: VerificationBundle.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GovernanceAck clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GovernanceAck copyWith(void Function(GovernanceAck) updates) =>
      super.copyWith((message) => updates(message as GovernanceAck))
          as GovernanceAck;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static GovernanceAck create() => GovernanceAck._();
  @$core.override
  GovernanceAck createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static GovernanceAck getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<GovernanceAck>(create);
  static GovernanceAck? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get accepted => $_getBF(0);
  @$pb.TagNumber(1)
  set accepted($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasAccepted() => $_has(0);
  @$pb.TagNumber(1)
  void clearAccepted() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get message => $_getSZ(1);
  @$pb.TagNumber(2)
  set message($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasMessage() => $_has(1);
  @$pb.TagNumber(2)
  void clearMessage() => $_clearField(2);

  @$pb.TagNumber(3)
  VerificationBundle get corporateBundle => $_getN(2);
  @$pb.TagNumber(3)
  set corporateBundle(VerificationBundle value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasCorporateBundle() => $_has(2);
  @$pb.TagNumber(3)
  void clearCorporateBundle() => $_clearField(3);
  @$pb.TagNumber(3)
  VerificationBundle ensureCorporateBundle() => $_ensure(2);
}

class AuthorizeCollaboratorRequest extends $pb.GeneratedMessage {
  factory AuthorizeCollaboratorRequest({
    CollaboratorAuthCert? cert,
    $core.String? collaboratorCpf,
  }) {
    final result = create();
    if (cert != null) result.cert = cert;
    if (collaboratorCpf != null) result.collaboratorCpf = collaboratorCpf;
    return result;
  }

  AuthorizeCollaboratorRequest._();

  factory AuthorizeCollaboratorRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory AuthorizeCollaboratorRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'AuthorizeCollaboratorRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOM<CollaboratorAuthCert>(1, _omitFieldNames ? '' : 'cert',
        subBuilder: CollaboratorAuthCert.create)
    ..aOS(2, _omitFieldNames ? '' : 'collaboratorCpf')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  AuthorizeCollaboratorRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  AuthorizeCollaboratorRequest copyWith(
          void Function(AuthorizeCollaboratorRequest) updates) =>
      super.copyWith(
              (message) => updates(message as AuthorizeCollaboratorRequest))
          as AuthorizeCollaboratorRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static AuthorizeCollaboratorRequest create() =>
      AuthorizeCollaboratorRequest._();
  @$core.override
  AuthorizeCollaboratorRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static AuthorizeCollaboratorRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<AuthorizeCollaboratorRequest>(create);
  static AuthorizeCollaboratorRequest? _defaultInstance;

  @$pb.TagNumber(1)
  CollaboratorAuthCert get cert => $_getN(0);
  @$pb.TagNumber(1)
  set cert(CollaboratorAuthCert value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasCert() => $_has(0);
  @$pb.TagNumber(1)
  void clearCert() => $_clearField(1);
  @$pb.TagNumber(1)
  CollaboratorAuthCert ensureCert() => $_ensure(0);

  @$pb.TagNumber(2)
  $core.String get collaboratorCpf => $_getSZ(1);
  @$pb.TagNumber(2)
  set collaboratorCpf($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasCollaboratorCpf() => $_has(1);
  @$pb.TagNumber(2)
  void clearCollaboratorCpf() => $_clearField(2);
}

class RevokeCollaboratorRequest extends $pb.GeneratedMessage {
  factory RevokeCollaboratorRequest({
    $core.String? ispCnpj,
    $core.String? partnerUuid,
    $core.String? collaboratorUuid,
    $core.String? reason,
    $fixnum.Int64? timestampMs,
    $core.List<$core.int>? partnerSignature,
  }) {
    final result = create();
    if (ispCnpj != null) result.ispCnpj = ispCnpj;
    if (partnerUuid != null) result.partnerUuid = partnerUuid;
    if (collaboratorUuid != null) result.collaboratorUuid = collaboratorUuid;
    if (reason != null) result.reason = reason;
    if (timestampMs != null) result.timestampMs = timestampMs;
    if (partnerSignature != null) result.partnerSignature = partnerSignature;
    return result;
  }

  RevokeCollaboratorRequest._();

  factory RevokeCollaboratorRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory RevokeCollaboratorRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'RevokeCollaboratorRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'ispCnpj')
    ..aOS(2, _omitFieldNames ? '' : 'partnerUuid')
    ..aOS(3, _omitFieldNames ? '' : 'collaboratorUuid')
    ..aOS(4, _omitFieldNames ? '' : 'reason')
    ..aInt64(5, _omitFieldNames ? '' : 'timestampMs')
    ..a<$core.List<$core.int>>(
        6, _omitFieldNames ? '' : 'partnerSignature', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RevokeCollaboratorRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RevokeCollaboratorRequest copyWith(
          void Function(RevokeCollaboratorRequest) updates) =>
      super.copyWith((message) => updates(message as RevokeCollaboratorRequest))
          as RevokeCollaboratorRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static RevokeCollaboratorRequest create() => RevokeCollaboratorRequest._();
  @$core.override
  RevokeCollaboratorRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static RevokeCollaboratorRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<RevokeCollaboratorRequest>(create);
  static RevokeCollaboratorRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get ispCnpj => $_getSZ(0);
  @$pb.TagNumber(1)
  set ispCnpj($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasIspCnpj() => $_has(0);
  @$pb.TagNumber(1)
  void clearIspCnpj() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get partnerUuid => $_getSZ(1);
  @$pb.TagNumber(2)
  set partnerUuid($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasPartnerUuid() => $_has(1);
  @$pb.TagNumber(2)
  void clearPartnerUuid() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get collaboratorUuid => $_getSZ(2);
  @$pb.TagNumber(3)
  set collaboratorUuid($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasCollaboratorUuid() => $_has(2);
  @$pb.TagNumber(3)
  void clearCollaboratorUuid() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get reason => $_getSZ(3);
  @$pb.TagNumber(4)
  set reason($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasReason() => $_has(3);
  @$pb.TagNumber(4)
  void clearReason() => $_clearField(4);

  @$pb.TagNumber(5)
  $fixnum.Int64 get timestampMs => $_getI64(4);
  @$pb.TagNumber(5)
  set timestampMs($fixnum.Int64 value) => $_setInt64(4, value);
  @$pb.TagNumber(5)
  $core.bool hasTimestampMs() => $_has(4);
  @$pb.TagNumber(5)
  void clearTimestampMs() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.List<$core.int> get partnerSignature => $_getN(5);
  @$pb.TagNumber(6)
  set partnerSignature($core.List<$core.int> value) => $_setBytes(5, value);
  @$pb.TagNumber(6)
  $core.bool hasPartnerSignature() => $_has(5);
  @$pb.TagNumber(6)
  void clearPartnerSignature() => $_clearField(6);
}

class UserVerificationConsent extends $pb.GeneratedMessage {
  factory UserVerificationConsent({
    $core.String? userUuid,
    $core.List<$core.int>? userEd25519Pub,
    $core.List<$core.int>? userX25519Pub,
    DocumentType? docType,
    $core.List<$core.int>? docSha256,
    $core.String? ispCnpj,
    $fixnum.Int64? timestampMs,
    $core.List<$core.int>? userSignature,
  }) {
    final result = create();
    if (userUuid != null) result.userUuid = userUuid;
    if (userEd25519Pub != null) result.userEd25519Pub = userEd25519Pub;
    if (userX25519Pub != null) result.userX25519Pub = userX25519Pub;
    if (docType != null) result.docType = docType;
    if (docSha256 != null) result.docSha256 = docSha256;
    if (ispCnpj != null) result.ispCnpj = ispCnpj;
    if (timestampMs != null) result.timestampMs = timestampMs;
    if (userSignature != null) result.userSignature = userSignature;
    return result;
  }

  UserVerificationConsent._();

  factory UserVerificationConsent.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory UserVerificationConsent.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'UserVerificationConsent',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'userUuid')
    ..a<$core.List<$core.int>>(
        2, _omitFieldNames ? '' : 'userEd25519Pub', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        3, _omitFieldNames ? '' : 'userX25519Pub', $pb.PbFieldType.OY)
    ..aE<DocumentType>(4, _omitFieldNames ? '' : 'docType',
        enumValues: DocumentType.values)
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'docSha256', $pb.PbFieldType.OY)
    ..aOS(6, _omitFieldNames ? '' : 'ispCnpj')
    ..aInt64(7, _omitFieldNames ? '' : 'timestampMs')
    ..a<$core.List<$core.int>>(
        8, _omitFieldNames ? '' : 'userSignature', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  UserVerificationConsent clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  UserVerificationConsent copyWith(
          void Function(UserVerificationConsent) updates) =>
      super.copyWith((message) => updates(message as UserVerificationConsent))
          as UserVerificationConsent;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static UserVerificationConsent create() => UserVerificationConsent._();
  @$core.override
  UserVerificationConsent createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static UserVerificationConsent getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<UserVerificationConsent>(create);
  static UserVerificationConsent? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get userUuid => $_getSZ(0);
  @$pb.TagNumber(1)
  set userUuid($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasUserUuid() => $_has(0);
  @$pb.TagNumber(1)
  void clearUserUuid() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.List<$core.int> get userEd25519Pub => $_getN(1);
  @$pb.TagNumber(2)
  set userEd25519Pub($core.List<$core.int> value) => $_setBytes(1, value);
  @$pb.TagNumber(2)
  $core.bool hasUserEd25519Pub() => $_has(1);
  @$pb.TagNumber(2)
  void clearUserEd25519Pub() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.List<$core.int> get userX25519Pub => $_getN(2);
  @$pb.TagNumber(3)
  set userX25519Pub($core.List<$core.int> value) => $_setBytes(2, value);
  @$pb.TagNumber(3)
  $core.bool hasUserX25519Pub() => $_has(2);
  @$pb.TagNumber(3)
  void clearUserX25519Pub() => $_clearField(3);

  @$pb.TagNumber(4)
  DocumentType get docType => $_getN(3);
  @$pb.TagNumber(4)
  set docType(DocumentType value) => $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasDocType() => $_has(3);
  @$pb.TagNumber(4)
  void clearDocType() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get docSha256 => $_getN(4);
  @$pb.TagNumber(5)
  set docSha256($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasDocSha256() => $_has(4);
  @$pb.TagNumber(5)
  void clearDocSha256() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get ispCnpj => $_getSZ(5);
  @$pb.TagNumber(6)
  set ispCnpj($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasIspCnpj() => $_has(5);
  @$pb.TagNumber(6)
  void clearIspCnpj() => $_clearField(6);

  @$pb.TagNumber(7)
  $fixnum.Int64 get timestampMs => $_getI64(6);
  @$pb.TagNumber(7)
  set timestampMs($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(7)
  $core.bool hasTimestampMs() => $_has(6);
  @$pb.TagNumber(7)
  void clearTimestampMs() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.List<$core.int> get userSignature => $_getN(7);
  @$pb.TagNumber(8)
  set userSignature($core.List<$core.int> value) => $_setBytes(7, value);
  @$pb.TagNumber(8)
  $core.bool hasUserSignature() => $_has(7);
  @$pb.TagNumber(8)
  void clearUserSignature() => $_clearField(8);
}

class SubmitVerificationRequest extends $pb.GeneratedMessage {
  factory SubmitVerificationRequest({
    $core.String? ispCnpj,
    CollaboratorAuthCert? collaboratorCert,
    UserVerificationConsent? userConsent,
    $core.String? normalizedDocument,
    $core.String? publicDisplayName,
    $core.String? homeNodeAddress,
    $fixnum.Int64? attestedAtMs,
    $core.List<$core.int>? collaboratorSignature,
  }) {
    final result = create();
    if (ispCnpj != null) result.ispCnpj = ispCnpj;
    if (collaboratorCert != null) result.collaboratorCert = collaboratorCert;
    if (userConsent != null) result.userConsent = userConsent;
    if (normalizedDocument != null)
      result.normalizedDocument = normalizedDocument;
    if (publicDisplayName != null) result.publicDisplayName = publicDisplayName;
    if (homeNodeAddress != null) result.homeNodeAddress = homeNodeAddress;
    if (attestedAtMs != null) result.attestedAtMs = attestedAtMs;
    if (collaboratorSignature != null)
      result.collaboratorSignature = collaboratorSignature;
    return result;
  }

  SubmitVerificationRequest._();

  factory SubmitVerificationRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory SubmitVerificationRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SubmitVerificationRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'ispCnpj')
    ..aOM<CollaboratorAuthCert>(2, _omitFieldNames ? '' : 'collaboratorCert',
        subBuilder: CollaboratorAuthCert.create)
    ..aOM<UserVerificationConsent>(3, _omitFieldNames ? '' : 'userConsent',
        subBuilder: UserVerificationConsent.create)
    ..aOS(4, _omitFieldNames ? '' : 'normalizedDocument')
    ..aOS(5, _omitFieldNames ? '' : 'publicDisplayName')
    ..aOS(6, _omitFieldNames ? '' : 'homeNodeAddress')
    ..aInt64(7, _omitFieldNames ? '' : 'attestedAtMs')
    ..a<$core.List<$core.int>>(
        8, _omitFieldNames ? '' : 'collaboratorSignature', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SubmitVerificationRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SubmitVerificationRequest copyWith(
          void Function(SubmitVerificationRequest) updates) =>
      super.copyWith((message) => updates(message as SubmitVerificationRequest))
          as SubmitVerificationRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static SubmitVerificationRequest create() => SubmitVerificationRequest._();
  @$core.override
  SubmitVerificationRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static SubmitVerificationRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SubmitVerificationRequest>(create);
  static SubmitVerificationRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get ispCnpj => $_getSZ(0);
  @$pb.TagNumber(1)
  set ispCnpj($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasIspCnpj() => $_has(0);
  @$pb.TagNumber(1)
  void clearIspCnpj() => $_clearField(1);

  @$pb.TagNumber(2)
  CollaboratorAuthCert get collaboratorCert => $_getN(1);
  @$pb.TagNumber(2)
  set collaboratorCert(CollaboratorAuthCert value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasCollaboratorCert() => $_has(1);
  @$pb.TagNumber(2)
  void clearCollaboratorCert() => $_clearField(2);
  @$pb.TagNumber(2)
  CollaboratorAuthCert ensureCollaboratorCert() => $_ensure(1);

  @$pb.TagNumber(3)
  UserVerificationConsent get userConsent => $_getN(2);
  @$pb.TagNumber(3)
  set userConsent(UserVerificationConsent value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasUserConsent() => $_has(2);
  @$pb.TagNumber(3)
  void clearUserConsent() => $_clearField(3);
  @$pb.TagNumber(3)
  UserVerificationConsent ensureUserConsent() => $_ensure(2);

  @$pb.TagNumber(4)
  $core.String get normalizedDocument => $_getSZ(3);
  @$pb.TagNumber(4)
  set normalizedDocument($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasNormalizedDocument() => $_has(3);
  @$pb.TagNumber(4)
  void clearNormalizedDocument() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get publicDisplayName => $_getSZ(4);
  @$pb.TagNumber(5)
  set publicDisplayName($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasPublicDisplayName() => $_has(4);
  @$pb.TagNumber(5)
  void clearPublicDisplayName() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get homeNodeAddress => $_getSZ(5);
  @$pb.TagNumber(6)
  set homeNodeAddress($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasHomeNodeAddress() => $_has(5);
  @$pb.TagNumber(6)
  void clearHomeNodeAddress() => $_clearField(6);

  @$pb.TagNumber(7)
  $fixnum.Int64 get attestedAtMs => $_getI64(6);
  @$pb.TagNumber(7)
  set attestedAtMs($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(7)
  $core.bool hasAttestedAtMs() => $_has(6);
  @$pb.TagNumber(7)
  void clearAttestedAtMs() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.List<$core.int> get collaboratorSignature => $_getN(7);
  @$pb.TagNumber(8)
  set collaboratorSignature($core.List<$core.int> value) =>
      $_setBytes(7, value);
  @$pb.TagNumber(8)
  $core.bool hasCollaboratorSignature() => $_has(7);
  @$pb.TagNumber(8)
  void clearCollaboratorSignature() => $_clearField(8);
}

class SubmitCpfRebindRequest extends $pb.GeneratedMessage {
  factory SubmitCpfRebindRequest({
    $core.String? ispCnpj,
    CollaboratorAuthCert? collaboratorCert,
    $core.String? oldUserUuid,
    UserVerificationConsent? newUserConsent,
    $core.String? normalizedCpf,
    $core.String? homeNodeAddress,
    $fixnum.Int64? attestedAtMs,
    $core.List<$core.int>? collaboratorSignature,
  }) {
    final result = create();
    if (ispCnpj != null) result.ispCnpj = ispCnpj;
    if (collaboratorCert != null) result.collaboratorCert = collaboratorCert;
    if (oldUserUuid != null) result.oldUserUuid = oldUserUuid;
    if (newUserConsent != null) result.newUserConsent = newUserConsent;
    if (normalizedCpf != null) result.normalizedCpf = normalizedCpf;
    if (homeNodeAddress != null) result.homeNodeAddress = homeNodeAddress;
    if (attestedAtMs != null) result.attestedAtMs = attestedAtMs;
    if (collaboratorSignature != null)
      result.collaboratorSignature = collaboratorSignature;
    return result;
  }

  SubmitCpfRebindRequest._();

  factory SubmitCpfRebindRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory SubmitCpfRebindRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SubmitCpfRebindRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'ispCnpj')
    ..aOM<CollaboratorAuthCert>(2, _omitFieldNames ? '' : 'collaboratorCert',
        subBuilder: CollaboratorAuthCert.create)
    ..aOS(3, _omitFieldNames ? '' : 'oldUserUuid')
    ..aOM<UserVerificationConsent>(4, _omitFieldNames ? '' : 'newUserConsent',
        subBuilder: UserVerificationConsent.create)
    ..aOS(5, _omitFieldNames ? '' : 'normalizedCpf')
    ..aOS(6, _omitFieldNames ? '' : 'homeNodeAddress')
    ..aInt64(7, _omitFieldNames ? '' : 'attestedAtMs')
    ..a<$core.List<$core.int>>(
        8, _omitFieldNames ? '' : 'collaboratorSignature', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SubmitCpfRebindRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SubmitCpfRebindRequest copyWith(
          void Function(SubmitCpfRebindRequest) updates) =>
      super.copyWith((message) => updates(message as SubmitCpfRebindRequest))
          as SubmitCpfRebindRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static SubmitCpfRebindRequest create() => SubmitCpfRebindRequest._();
  @$core.override
  SubmitCpfRebindRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static SubmitCpfRebindRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SubmitCpfRebindRequest>(create);
  static SubmitCpfRebindRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get ispCnpj => $_getSZ(0);
  @$pb.TagNumber(1)
  set ispCnpj($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasIspCnpj() => $_has(0);
  @$pb.TagNumber(1)
  void clearIspCnpj() => $_clearField(1);

  @$pb.TagNumber(2)
  CollaboratorAuthCert get collaboratorCert => $_getN(1);
  @$pb.TagNumber(2)
  set collaboratorCert(CollaboratorAuthCert value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasCollaboratorCert() => $_has(1);
  @$pb.TagNumber(2)
  void clearCollaboratorCert() => $_clearField(2);
  @$pb.TagNumber(2)
  CollaboratorAuthCert ensureCollaboratorCert() => $_ensure(1);

  @$pb.TagNumber(3)
  $core.String get oldUserUuid => $_getSZ(2);
  @$pb.TagNumber(3)
  set oldUserUuid($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasOldUserUuid() => $_has(2);
  @$pb.TagNumber(3)
  void clearOldUserUuid() => $_clearField(3);

  @$pb.TagNumber(4)
  UserVerificationConsent get newUserConsent => $_getN(3);
  @$pb.TagNumber(4)
  set newUserConsent(UserVerificationConsent value) => $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasNewUserConsent() => $_has(3);
  @$pb.TagNumber(4)
  void clearNewUserConsent() => $_clearField(4);
  @$pb.TagNumber(4)
  UserVerificationConsent ensureNewUserConsent() => $_ensure(3);

  @$pb.TagNumber(5)
  $core.String get normalizedCpf => $_getSZ(4);
  @$pb.TagNumber(5)
  set normalizedCpf($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasNormalizedCpf() => $_has(4);
  @$pb.TagNumber(5)
  void clearNormalizedCpf() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get homeNodeAddress => $_getSZ(5);
  @$pb.TagNumber(6)
  set homeNodeAddress($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasHomeNodeAddress() => $_has(5);
  @$pb.TagNumber(6)
  void clearHomeNodeAddress() => $_clearField(6);

  @$pb.TagNumber(7)
  $fixnum.Int64 get attestedAtMs => $_getI64(6);
  @$pb.TagNumber(7)
  set attestedAtMs($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(7)
  $core.bool hasAttestedAtMs() => $_has(6);
  @$pb.TagNumber(7)
  void clearAttestedAtMs() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.List<$core.int> get collaboratorSignature => $_getN(7);
  @$pb.TagNumber(8)
  set collaboratorSignature($core.List<$core.int> value) =>
      $_setBytes(7, value);
  @$pb.TagNumber(8)
  $core.bool hasCollaboratorSignature() => $_has(7);
  @$pb.TagNumber(8)
  void clearCollaboratorSignature() => $_clearField(8);
}

class GetCredentialRequest extends $pb.GeneratedMessage {
  factory GetCredentialRequest({
    $core.String? userUuid,
    $core.String? deviceId,
    $fixnum.Int64? timestampMs,
    $core.List<$core.int>? callerPublicKey,
    $core.List<$core.int>? signature,
    DeviceDelegationCert? delegationCert,
  }) {
    final result = create();
    if (userUuid != null) result.userUuid = userUuid;
    if (deviceId != null) result.deviceId = deviceId;
    if (timestampMs != null) result.timestampMs = timestampMs;
    if (callerPublicKey != null) result.callerPublicKey = callerPublicKey;
    if (signature != null) result.signature = signature;
    if (delegationCert != null) result.delegationCert = delegationCert;
    return result;
  }

  GetCredentialRequest._();

  factory GetCredentialRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory GetCredentialRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'GetCredentialRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'userUuid')
    ..aOS(2, _omitFieldNames ? '' : 'deviceId')
    ..aInt64(3, _omitFieldNames ? '' : 'timestampMs')
    ..a<$core.List<$core.int>>(
        4, _omitFieldNames ? '' : 'callerPublicKey', $pb.PbFieldType.OY)
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'signature', $pb.PbFieldType.OY)
    ..aOM<DeviceDelegationCert>(6, _omitFieldNames ? '' : 'delegationCert',
        subBuilder: DeviceDelegationCert.create)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GetCredentialRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GetCredentialRequest copyWith(void Function(GetCredentialRequest) updates) =>
      super.copyWith((message) => updates(message as GetCredentialRequest))
          as GetCredentialRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static GetCredentialRequest create() => GetCredentialRequest._();
  @$core.override
  GetCredentialRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static GetCredentialRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<GetCredentialRequest>(create);
  static GetCredentialRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get userUuid => $_getSZ(0);
  @$pb.TagNumber(1)
  set userUuid($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasUserUuid() => $_has(0);
  @$pb.TagNumber(1)
  void clearUserUuid() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get deviceId => $_getSZ(1);
  @$pb.TagNumber(2)
  set deviceId($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasDeviceId() => $_has(1);
  @$pb.TagNumber(2)
  void clearDeviceId() => $_clearField(2);

  @$pb.TagNumber(3)
  $fixnum.Int64 get timestampMs => $_getI64(2);
  @$pb.TagNumber(3)
  set timestampMs($fixnum.Int64 value) => $_setInt64(2, value);
  @$pb.TagNumber(3)
  $core.bool hasTimestampMs() => $_has(2);
  @$pb.TagNumber(3)
  void clearTimestampMs() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.List<$core.int> get callerPublicKey => $_getN(3);
  @$pb.TagNumber(4)
  set callerPublicKey($core.List<$core.int> value) => $_setBytes(3, value);
  @$pb.TagNumber(4)
  $core.bool hasCallerPublicKey() => $_has(3);
  @$pb.TagNumber(4)
  void clearCallerPublicKey() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get signature => $_getN(4);
  @$pb.TagNumber(5)
  set signature($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasSignature() => $_has(4);
  @$pb.TagNumber(5)
  void clearSignature() => $_clearField(5);

  @$pb.TagNumber(6)
  DeviceDelegationCert get delegationCert => $_getN(5);
  @$pb.TagNumber(6)
  set delegationCert(DeviceDelegationCert value) => $_setField(6, value);
  @$pb.TagNumber(6)
  $core.bool hasDelegationCert() => $_has(5);
  @$pb.TagNumber(6)
  void clearDelegationCert() => $_clearField(6);
  @$pb.TagNumber(6)
  DeviceDelegationCert ensureDelegationCert() => $_ensure(5);
}

class RevokeDeviceRequest extends $pb.GeneratedMessage {
  factory RevokeDeviceRequest({
    $core.String? userUuid,
    $core.String? revokedDeviceId,
    $core.String? reason,
    $fixnum.Int64? timestampMs,
    $core.List<$core.int>? rootSignature,
  }) {
    final result = create();
    if (userUuid != null) result.userUuid = userUuid;
    if (revokedDeviceId != null) result.revokedDeviceId = revokedDeviceId;
    if (reason != null) result.reason = reason;
    if (timestampMs != null) result.timestampMs = timestampMs;
    if (rootSignature != null) result.rootSignature = rootSignature;
    return result;
  }

  RevokeDeviceRequest._();

  factory RevokeDeviceRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory RevokeDeviceRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'RevokeDeviceRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'userUuid')
    ..aOS(2, _omitFieldNames ? '' : 'revokedDeviceId')
    ..aOS(3, _omitFieldNames ? '' : 'reason')
    ..aInt64(4, _omitFieldNames ? '' : 'timestampMs')
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'rootSignature', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RevokeDeviceRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RevokeDeviceRequest copyWith(void Function(RevokeDeviceRequest) updates) =>
      super.copyWith((message) => updates(message as RevokeDeviceRequest))
          as RevokeDeviceRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static RevokeDeviceRequest create() => RevokeDeviceRequest._();
  @$core.override
  RevokeDeviceRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static RevokeDeviceRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<RevokeDeviceRequest>(create);
  static RevokeDeviceRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get userUuid => $_getSZ(0);
  @$pb.TagNumber(1)
  set userUuid($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasUserUuid() => $_has(0);
  @$pb.TagNumber(1)
  void clearUserUuid() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get revokedDeviceId => $_getSZ(1);
  @$pb.TagNumber(2)
  set revokedDeviceId($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasRevokedDeviceId() => $_has(1);
  @$pb.TagNumber(2)
  void clearRevokedDeviceId() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get reason => $_getSZ(2);
  @$pb.TagNumber(3)
  set reason($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasReason() => $_has(2);
  @$pb.TagNumber(3)
  void clearReason() => $_clearField(3);

  @$pb.TagNumber(4)
  $fixnum.Int64 get timestampMs => $_getI64(3);
  @$pb.TagNumber(4)
  set timestampMs($fixnum.Int64 value) => $_setInt64(3, value);
  @$pb.TagNumber(4)
  $core.bool hasTimestampMs() => $_has(3);
  @$pb.TagNumber(4)
  void clearTimestampMs() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get rootSignature => $_getN(4);
  @$pb.TagNumber(5)
  set rootSignature($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasRootSignature() => $_has(4);
  @$pb.TagNumber(5)
  void clearRootSignature() => $_clearField(5);
}

class PortAccountRequest extends $pb.GeneratedMessage {
  factory PortAccountRequest({
    $core.String? userUuid,
    $core.String? targetHomeNodeAddress,
    $core.String? targetIspCnpj,
    $fixnum.Int64? expectedRoutingEpoch,
    $fixnum.Int64? timestampMs,
    $core.List<$core.int>? rootSignature,
    CollaboratorAuthCert? targetCollaboratorCert,
    $core.List<$core.int>? targetCollaboratorSignature,
  }) {
    final result = create();
    if (userUuid != null) result.userUuid = userUuid;
    if (targetHomeNodeAddress != null)
      result.targetHomeNodeAddress = targetHomeNodeAddress;
    if (targetIspCnpj != null) result.targetIspCnpj = targetIspCnpj;
    if (expectedRoutingEpoch != null)
      result.expectedRoutingEpoch = expectedRoutingEpoch;
    if (timestampMs != null) result.timestampMs = timestampMs;
    if (rootSignature != null) result.rootSignature = rootSignature;
    if (targetCollaboratorCert != null)
      result.targetCollaboratorCert = targetCollaboratorCert;
    if (targetCollaboratorSignature != null)
      result.targetCollaboratorSignature = targetCollaboratorSignature;
    return result;
  }

  PortAccountRequest._();

  factory PortAccountRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory PortAccountRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'PortAccountRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aOS(1, _omitFieldNames ? '' : 'userUuid')
    ..aOS(2, _omitFieldNames ? '' : 'targetHomeNodeAddress')
    ..aOS(3, _omitFieldNames ? '' : 'targetIspCnpj')
    ..aInt64(4, _omitFieldNames ? '' : 'expectedRoutingEpoch')
    ..aInt64(5, _omitFieldNames ? '' : 'timestampMs')
    ..a<$core.List<$core.int>>(
        6, _omitFieldNames ? '' : 'rootSignature', $pb.PbFieldType.OY)
    ..aOM<CollaboratorAuthCert>(
        7, _omitFieldNames ? '' : 'targetCollaboratorCert',
        subBuilder: CollaboratorAuthCert.create)
    ..a<$core.List<$core.int>>(
        8,
        _omitFieldNames ? '' : 'targetCollaboratorSignature',
        $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PortAccountRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PortAccountRequest copyWith(void Function(PortAccountRequest) updates) =>
      super.copyWith((message) => updates(message as PortAccountRequest))
          as PortAccountRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static PortAccountRequest create() => PortAccountRequest._();
  @$core.override
  PortAccountRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static PortAccountRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<PortAccountRequest>(create);
  static PortAccountRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get userUuid => $_getSZ(0);
  @$pb.TagNumber(1)
  set userUuid($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasUserUuid() => $_has(0);
  @$pb.TagNumber(1)
  void clearUserUuid() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get targetHomeNodeAddress => $_getSZ(1);
  @$pb.TagNumber(2)
  set targetHomeNodeAddress($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasTargetHomeNodeAddress() => $_has(1);
  @$pb.TagNumber(2)
  void clearTargetHomeNodeAddress() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get targetIspCnpj => $_getSZ(2);
  @$pb.TagNumber(3)
  set targetIspCnpj($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasTargetIspCnpj() => $_has(2);
  @$pb.TagNumber(3)
  void clearTargetIspCnpj() => $_clearField(3);

  @$pb.TagNumber(4)
  $fixnum.Int64 get expectedRoutingEpoch => $_getI64(3);
  @$pb.TagNumber(4)
  set expectedRoutingEpoch($fixnum.Int64 value) => $_setInt64(3, value);
  @$pb.TagNumber(4)
  $core.bool hasExpectedRoutingEpoch() => $_has(3);
  @$pb.TagNumber(4)
  void clearExpectedRoutingEpoch() => $_clearField(4);

  @$pb.TagNumber(5)
  $fixnum.Int64 get timestampMs => $_getI64(4);
  @$pb.TagNumber(5)
  set timestampMs($fixnum.Int64 value) => $_setInt64(4, value);
  @$pb.TagNumber(5)
  $core.bool hasTimestampMs() => $_has(4);
  @$pb.TagNumber(5)
  void clearTimestampMs() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.List<$core.int> get rootSignature => $_getN(5);
  @$pb.TagNumber(6)
  set rootSignature($core.List<$core.int> value) => $_setBytes(5, value);
  @$pb.TagNumber(6)
  $core.bool hasRootSignature() => $_has(5);
  @$pb.TagNumber(6)
  void clearRootSignature() => $_clearField(6);

  @$pb.TagNumber(7)
  CollaboratorAuthCert get targetCollaboratorCert => $_getN(6);
  @$pb.TagNumber(7)
  set targetCollaboratorCert(CollaboratorAuthCert value) =>
      $_setField(7, value);
  @$pb.TagNumber(7)
  $core.bool hasTargetCollaboratorCert() => $_has(6);
  @$pb.TagNumber(7)
  void clearTargetCollaboratorCert() => $_clearField(7);
  @$pb.TagNumber(7)
  CollaboratorAuthCert ensureTargetCollaboratorCert() => $_ensure(6);

  @$pb.TagNumber(8)
  $core.List<$core.int> get targetCollaboratorSignature => $_getN(7);
  @$pb.TagNumber(8)
  set targetCollaboratorSignature($core.List<$core.int> value) =>
      $_setBytes(7, value);
  @$pb.TagNumber(8)
  $core.bool hasTargetCollaboratorSignature() => $_has(7);
  @$pb.TagNumber(8)
  void clearTargetCollaboratorSignature() => $_clearField(8);
}

class StreamRevocationsRequest extends $pb.GeneratedMessage {
  factory StreamRevocationsRequest({
    $fixnum.Int64? sinceRevocationSeq,
  }) {
    final result = create();
    if (sinceRevocationSeq != null)
      result.sinceRevocationSeq = sinceRevocationSeq;
    return result;
  }

  StreamRevocationsRequest._();

  factory StreamRevocationsRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromBuffer(data, registry);
  factory StreamRevocationsRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      create()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'StreamRevocationsRequest',
      package:
          const $pb.PackageName(_omitMessageNames ? '' : 'br.dev.ispchat.grpc'),
      createEmptyInstance: create)
    ..aInt64(1, _omitFieldNames ? '' : 'sinceRevocationSeq')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StreamRevocationsRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StreamRevocationsRequest copyWith(
          void Function(StreamRevocationsRequest) updates) =>
      super.copyWith((message) => updates(message as StreamRevocationsRequest))
          as StreamRevocationsRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  static StreamRevocationsRequest create() => StreamRevocationsRequest._();
  @$core.override
  StreamRevocationsRequest createEmptyInstance() => create();
  @$core.pragma('dart2js:noInline')
  static StreamRevocationsRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<StreamRevocationsRequest>(create);
  static StreamRevocationsRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $fixnum.Int64 get sinceRevocationSeq => $_getI64(0);
  @$pb.TagNumber(1)
  set sinceRevocationSeq($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasSinceRevocationSeq() => $_has(0);
  @$pb.TagNumber(1)
  void clearSinceRevocationSeq() => $_clearField(1);
}

const $core.bool _omitFieldNames =
    $core.bool.fromEnvironment('protobuf.omit_field_names');
const $core.bool _omitMessageNames =
    $core.bool.fromEnvironment('protobuf.omit_message_names');
