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

import 'package:protobuf/protobuf.dart' as $pb;

/// Tipos de mensagens e eventos trafegados na rede
class MessageType extends $pb.ProtobufEnum {
  static const MessageType UNKNOWN =
      MessageType._(0, _omitEnumNames ? '' : 'UNKNOWN');
  static const MessageType TEXT =
      MessageType._(1, _omitEnumNames ? '' : 'TEXT');
  static const MessageType RING =
      MessageType._(2, _omitEnumNames ? '' : 'RING');
  static const MessageType SDP_OFFER =
      MessageType._(3, _omitEnumNames ? '' : 'SDP_OFFER');
  static const MessageType SDP_ANSWER =
      MessageType._(4, _omitEnumNames ? '' : 'SDP_ANSWER');
  static const MessageType ICE_CANDIDATE =
      MessageType._(5, _omitEnumNames ? '' : 'ICE_CANDIDATE');
  static const MessageType CALL_DECLINE =
      MessageType._(6, _omitEnumNames ? '' : 'CALL_DECLINE');
  static const MessageType AUTH_HANDSHAKE =
      MessageType._(7, _omitEnumNames ? '' : 'AUTH_HANDSHAKE');
  static const MessageType SERVER_QUEUED_ACK =
      MessageType._(8, _omitEnumNames ? '' : 'SERVER_QUEUED_ACK');
  static const MessageType DELIVERY_ACK =
      MessageType._(9, _omitEnumNames ? '' : 'DELIVERY_ACK');
  static const MessageType DELIVERY_ERROR =
      MessageType._(10, _omitEnumNames ? '' : 'DELIVERY_ERROR');
  static const MessageType IDENTITY_REPLACED =
      MessageType._(11, _omitEnumNames ? '' : 'IDENTITY_REPLACED');
  static const MessageType REVOCATION_NOTICE =
      MessageType._(12, _omitEnumNames ? '' : 'REVOCATION_NOTICE');

  static const $core.List<MessageType> values = <MessageType>[
    UNKNOWN,
    TEXT,
    RING,
    SDP_OFFER,
    SDP_ANSWER,
    ICE_CANDIDATE,
    CALL_DECLINE,
    AUTH_HANDSHAKE,
    SERVER_QUEUED_ACK,
    DELIVERY_ACK,
    DELIVERY_ERROR,
    IDENTITY_REPLACED,
    REVOCATION_NOTICE,
  ];

  static final $core.List<MessageType?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 12);
  static MessageType? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const MessageType._(super.value, super.name);
}

/// Papel criptografico do dispositivo na conta
class DeviceRole extends $pb.ProtobufEnum {
  static const DeviceRole MOBILE_ROOT =
      DeviceRole._(0, _omitEnumNames ? '' : 'MOBILE_ROOT');
  static const DeviceRole DESKTOP =
      DeviceRole._(1, _omitEnumNames ? '' : 'DESKTOP');
  static const DeviceRole ERP_API =
      DeviceRole._(2, _omitEnumNames ? '' : 'ERP_API');

  static const $core.List<DeviceRole> values = <DeviceRole>[
    MOBILE_ROOT,
    DESKTOP,
    ERP_API,
  ];

  static final $core.List<DeviceRole?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 2);
  static DeviceRole? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const DeviceRole._(super.value, super.name);
}

/// Tipo de documento verificado na Cadeia de Confianca (Chain of Trust)
class DocumentType extends $pb.ProtobufEnum {
  static const DocumentType DOC_UNKNOWN =
      DocumentType._(0, _omitEnumNames ? '' : 'DOC_UNKNOWN');
  static const DocumentType CPF =
      DocumentType._(1, _omitEnumNames ? '' : 'CPF');
  static const DocumentType CNPJ =
      DocumentType._(2, _omitEnumNames ? '' : 'CNPJ');

  static const $core.List<DocumentType> values = <DocumentType>[
    DOC_UNKNOWN,
    CPF,
    CNPJ,
  ];

  static final $core.List<DocumentType?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 2);
  static DocumentType? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const DocumentType._(super.value, super.name);
}

/// Resultado detalhado do roteamento inter-provedores / No Casa
class DeliveryStatus extends $pb.ProtobufEnum {
  static const DeliveryStatus DELIVERY_UNKNOWN =
      DeliveryStatus._(0, _omitEnumNames ? '' : 'DELIVERY_UNKNOWN');
  static const DeliveryStatus LIVE_DELIVERED =
      DeliveryStatus._(1, _omitEnumNames ? '' : 'LIVE_DELIVERED');
  static const DeliveryStatus HOME_QUEUED =
      DeliveryStatus._(2, _omitEnumNames ? '' : 'HOME_QUEUED');
  static const DeliveryStatus REDIRECT_REQUIRED =
      DeliveryStatus._(3, _omitEnumNames ? '' : 'REDIRECT_REQUIRED');
  static const DeliveryStatus REJECTED =
      DeliveryStatus._(4, _omitEnumNames ? '' : 'REJECTED');

  static const $core.List<DeliveryStatus> values = <DeliveryStatus>[
    DELIVERY_UNKNOWN,
    LIVE_DELIVERED,
    HOME_QUEUED,
    REDIRECT_REQUIRED,
    REJECTED,
  ];

  static final $core.List<DeliveryStatus?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 4);
  static DeliveryStatus? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const DeliveryStatus._(super.value, super.name);
}

const $core.bool _omitEnumNames =
    $core.bool.fromEnvironment('protobuf.omit_enum_names');
