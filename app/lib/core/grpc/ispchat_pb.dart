import 'dart:typed_data';

import 'package:grpc/grpc.dart';
import 'package:protobuf/protobuf.dart';

/// Enum espelhado de `MessageType` em `node/src/main/proto/ispchat.proto`.
enum ProtoMessageType {
  unknown(0),
  text(1),
  ring(2),
  sdpOffer(3),
  sdpAnswer(4),
  iceCandidate(5),
  callDecline(6),
  authHandshake(7);

  final int value;
  const ProtoMessageType(this.value);

  static ProtoMessageType fromValue(int value) {
    for (final ProtoMessageType t in ProtoMessageType.values) {
      if (t.value == value) return t;
    }
    return ProtoMessageType.unknown;
  }
}

/// Espelho binario wire-compatible de `RegisterSessionRequest` (ispchat.proto).
class RegisterSessionRequestProto {
  final String userUuid;
  final String nodeAddress;
  final Uint8List publicKey;
  final Uint8List x25519PublicKey;
  final Uint8List signature;
  final int timestamp;

  const RegisterSessionRequestProto({
    required this.userUuid,
    required this.nodeAddress,
    required this.publicKey,
    required this.x25519PublicKey,
    required this.signature,
    required this.timestamp,
  });

  Uint8List writeToBuffer() {
    final CodedBufferWriter writer = CodedBufferWriter();
    if (userUuid.isNotEmpty) writer.writeField(1, PbFieldType.OS, userUuid);
    if (nodeAddress.isNotEmpty) writer.writeField(2, PbFieldType.OS, nodeAddress);
    if (publicKey.isNotEmpty) writer.writeField(3, PbFieldType.OY, publicKey);
    if (x25519PublicKey.isNotEmpty) writer.writeField(4, PbFieldType.OY, x25519PublicKey);
    if (signature.isNotEmpty) writer.writeField(5, PbFieldType.OY, signature);
    if (timestamp != 0) writer.writeField(6, PbFieldType.O6, Int64Placeholder.fromInt(timestamp));
    return writer.toBuffer();
  }

  factory RegisterSessionRequestProto.fromBuffer(List<int> bytes) {
    final CodedBufferReader reader = CodedBufferReader(bytes);
    String userUuid = '';
    String nodeAddress = '';
    Uint8List publicKey = Uint8List(0);
    Uint8List x25519PublicKey = Uint8List(0);
    Uint8List signature = Uint8List(0);
    int timestamp = 0;
    while (!reader.isAtEnd()) {
      final int tag = reader.readTag();
      final int fieldNumber = getTagFieldNumber(tag);
      switch (fieldNumber) {
        case 1:
          userUuid = reader.readString();
        case 2:
          nodeAddress = reader.readString();
        case 3:
          publicKey = Uint8List.fromList(reader.readBytes());
        case 4:
          x25519PublicKey = Uint8List.fromList(reader.readBytes());
        case 5:
          signature = Uint8List.fromList(reader.readBytes());
        case 6:
          timestamp = reader.readInt64().toInt();
        default:
          reader.skipField(tag);
      }
    }
    return RegisterSessionRequestProto(
      userUuid: userUuid,
      nodeAddress: nodeAddress,
      publicKey: publicKey,
      x25519PublicKey: x25519PublicKey,
      signature: signature,
      timestamp: timestamp,
    );
  }
}

/// Espelho binario wire-compatible de `RegisterSessionResponse` (ispchat.proto).
class RegisterSessionResponseProto {
  final bool success;
  final String errorMessage;

  const RegisterSessionResponseProto({
    required this.success,
    required this.errorMessage,
  });

  Uint8List writeToBuffer() {
    final CodedBufferWriter writer = CodedBufferWriter();
    if (success) writer.writeField(1, PbFieldType.OB, success);
    if (errorMessage.isNotEmpty) writer.writeField(2, PbFieldType.OS, errorMessage);
    return writer.toBuffer();
  }

  factory RegisterSessionResponseProto.fromBuffer(List<int> bytes) {
    final CodedBufferReader reader = CodedBufferReader(bytes);
    bool success = false;
    String errorMessage = '';
    while (!reader.isAtEnd()) {
      final int tag = reader.readTag();
      final int fieldNumber = getTagFieldNumber(tag);
      switch (fieldNumber) {
        case 1:
          success = reader.readBool();
        case 2:
          errorMessage = reader.readString();
        default:
          reader.skipField(tag);
      }
    }
    return RegisterSessionResponseProto(success: success, errorMessage: errorMessage);
  }
}

/// Espelho binario wire-compatible de `ClientEnvelope` (ispchat.proto).
class ClientEnvelopeProto {
  final String messageId;
  final String senderUuid;
  final String receiverUuid;
  final ProtoMessageType type;
  final Uint8List encryptedPayload;
  final Uint8List senderPublicKey;
  final Uint8List signature;
  final int timestamp;

  const ClientEnvelopeProto({
    required this.messageId,
    required this.senderUuid,
    required this.receiverUuid,
    required this.type,
    required this.encryptedPayload,
    required this.senderPublicKey,
    required this.signature,
    required this.timestamp,
  });

  Uint8List writeToBuffer() {
    final CodedBufferWriter writer = CodedBufferWriter();
    if (messageId.isNotEmpty) writer.writeField(1, PbFieldType.OS, messageId);
    if (senderUuid.isNotEmpty) writer.writeField(2, PbFieldType.OS, senderUuid);
    if (receiverUuid.isNotEmpty) writer.writeField(3, PbFieldType.OS, receiverUuid);
    if (type.value != 0) writer.writeField(4, PbFieldType.O3, type.value);
    if (encryptedPayload.isNotEmpty) writer.writeField(5, PbFieldType.OY, encryptedPayload);
    if (senderPublicKey.isNotEmpty) writer.writeField(6, PbFieldType.OY, senderPublicKey);
    if (signature.isNotEmpty) writer.writeField(7, PbFieldType.OY, signature);
    if (timestamp != 0) writer.writeField(8, PbFieldType.O6, Int64Placeholder.fromInt(timestamp));
    return writer.toBuffer();
  }

  factory ClientEnvelopeProto.fromBuffer(List<int> bytes) {
    final CodedBufferReader reader = CodedBufferReader(bytes);
    String messageId = '';
    String senderUuid = '';
    String receiverUuid = '';
    ProtoMessageType type = ProtoMessageType.unknown;
    Uint8List encryptedPayload = Uint8List(0);
    Uint8List senderPublicKey = Uint8List(0);
    Uint8List signature = Uint8List(0);
    int timestamp = 0;

    while (!reader.isAtEnd()) {
      final int tag = reader.readTag();
      final int fieldNumber = getTagFieldNumber(tag);
      switch (fieldNumber) {
        case 1:
          messageId = reader.readString();
        case 2:
          senderUuid = reader.readString();
        case 3:
          receiverUuid = reader.readString();
        case 4:
          type = ProtoMessageType.fromValue(reader.readEnum());
        case 5:
          encryptedPayload = Uint8List.fromList(reader.readBytes());
        case 6:
          senderPublicKey = Uint8List.fromList(reader.readBytes());
        case 7:
          signature = Uint8List.fromList(reader.readBytes());
        case 8:
          timestamp = reader.readInt64().toInt();
        default:
          reader.skipField(tag);
      }
    }

    return ClientEnvelopeProto(
      messageId: messageId,
      senderUuid: senderUuid,
      receiverUuid: receiverUuid,
      type: type,
      encryptedPayload: encryptedPayload,
      senderPublicKey: senderPublicKey,
      signature: signature,
      timestamp: timestamp,
    );
  }
}

/// Espelho binario wire-compatible de `ServerEnvelope` (ispchat.proto).
class ServerEnvelopeProto {
  final String messageId;
  final String senderUuid;
  final ProtoMessageType type;
  final Uint8List encryptedPayload;
  final Uint8List senderPublicKey;
  final Uint8List signature;
  final int timestamp;

  const ServerEnvelopeProto({
    required this.messageId,
    required this.senderUuid,
    required this.type,
    required this.encryptedPayload,
    required this.senderPublicKey,
    required this.signature,
    required this.timestamp,
  });

  Uint8List writeToBuffer() {
    final CodedBufferWriter writer = CodedBufferWriter();
    if (messageId.isNotEmpty) writer.writeField(1, PbFieldType.OS, messageId);
    if (senderUuid.isNotEmpty) writer.writeField(2, PbFieldType.OS, senderUuid);
    if (type.value != 0) writer.writeField(3, PbFieldType.O3, type.value);
    if (encryptedPayload.isNotEmpty) writer.writeField(4, PbFieldType.OY, encryptedPayload);
    if (senderPublicKey.isNotEmpty) writer.writeField(5, PbFieldType.OY, senderPublicKey);
    if (signature.isNotEmpty) writer.writeField(6, PbFieldType.OY, signature);
    if (timestamp != 0) writer.writeField(7, PbFieldType.O6, Int64Placeholder.fromInt(timestamp));
    return writer.toBuffer();
  }

  factory ServerEnvelopeProto.fromBuffer(List<int> bytes) {
    final CodedBufferReader reader = CodedBufferReader(bytes);
    String messageId = '';
    String senderUuid = '';
    ProtoMessageType type = ProtoMessageType.unknown;
    Uint8List encryptedPayload = Uint8List(0);
    Uint8List senderPublicKey = Uint8List(0);
    Uint8List signature = Uint8List(0);
    int timestamp = 0;

    while (!reader.isAtEnd()) {
      final int tag = reader.readTag();
      final int fieldNumber = getTagFieldNumber(tag);
      switch (fieldNumber) {
        case 1:
          messageId = reader.readString();
        case 2:
          senderUuid = reader.readString();
        case 3:
          type = ProtoMessageType.fromValue(reader.readEnum());
        case 4:
          encryptedPayload = Uint8List.fromList(reader.readBytes());
        case 5:
          senderPublicKey = Uint8List.fromList(reader.readBytes());
        case 6:
          signature = Uint8List.fromList(reader.readBytes());
        case 7:
          timestamp = reader.readInt64().toInt();
        default:
          reader.skipField(tag);
      }
    }

    return ServerEnvelopeProto(
      messageId: messageId,
      senderUuid: senderUuid,
      type: type,
      encryptedPayload: encryptedPayload,
      senderPublicKey: senderPublicKey,
      signature: signature,
      timestamp: timestamp,
    );
  }
}

/// Helper para passar int64 ao CodedBufferWriter usando o tipo Int64 reexportado pelo protobuf.
class Int64Placeholder {
  static dynamic fromInt(int value) {
    final CodedBufferReader reader = CodedBufferReader(_encodeVarint64(value));
    return reader.readInt64();
  }

  static Uint8List _encodeVarint64(int value) {
    final List<int> bytes = <int>[];
    int v = value;
    while (v >= 0x80 || v < 0) {
      bytes.add((v & 0x7F) | 0x80);
      v = v >>> 7;
      if (bytes.length >= 9) {
        bytes.add(v & 0xFF);
        return Uint8List.fromList(bytes);
      }
    }
    bytes.add(v & 0x7F);
    return Uint8List.fromList(bytes);
  }
}

/// Espelho binario wire-compatible de `FindUserRequest` (ispchat.proto).
class FindUserRequestProto {
  final String targetUuid;

  const FindUserRequestProto({required this.targetUuid});

  Uint8List writeToBuffer() {
    final CodedBufferWriter writer = CodedBufferWriter();
    if (targetUuid.isNotEmpty) writer.writeField(1, PbFieldType.OS, targetUuid);
    return writer.toBuffer();
  }

  factory FindUserRequestProto.fromBuffer(List<int> bytes) {
    final CodedBufferReader reader = CodedBufferReader(bytes);
    String targetUuid = '';
    while (!reader.isAtEnd()) {
      final int tag = reader.readTag();
      final int fieldNumber = getTagFieldNumber(tag);
      switch (fieldNumber) {
        case 1:
          targetUuid = reader.readString();
        default:
          reader.skipField(tag);
      }
    }
    return FindUserRequestProto(targetUuid: targetUuid);
  }
}

/// Espelho binario wire-compatible de `FindUserResponse` (ispchat.proto).
class FindUserResponseProto {
  final bool found;
  final String nodeAddress;
  final Uint8List publicKey;
  final Uint8List x25519PublicKey;

  const FindUserResponseProto({
    required this.found,
    this.nodeAddress = '',
    required this.publicKey,
    required this.x25519PublicKey,
  });

  Uint8List writeToBuffer() {
    final CodedBufferWriter writer = CodedBufferWriter();
    if (found) writer.writeField(1, PbFieldType.OB, found);
    if (nodeAddress.isNotEmpty) writer.writeField(2, PbFieldType.OS, nodeAddress);
    if (publicKey.isNotEmpty) writer.writeField(3, PbFieldType.OY, publicKey);
    if (x25519PublicKey.isNotEmpty) writer.writeField(4, PbFieldType.OY, x25519PublicKey);
    return writer.toBuffer();
  }

  factory FindUserResponseProto.fromBuffer(List<int> bytes) {
    final CodedBufferReader reader = CodedBufferReader(bytes);
    bool found = false;
    String nodeAddress = '';
    Uint8List publicKey = Uint8List(0);
    Uint8List x25519PublicKey = Uint8List(0);
    while (!reader.isAtEnd()) {
      final int tag = reader.readTag();
      final int fieldNumber = getTagFieldNumber(tag);
      switch (fieldNumber) {
        case 1:
          found = reader.readBool();
        case 2:
          nodeAddress = reader.readString();
        case 3:
          publicKey = Uint8List.fromList(reader.readBytes());
        case 4:
          x25519PublicKey = Uint8List.fromList(reader.readBytes());
        default:
          reader.skipField(tag);
      }
    }
    return FindUserResponseProto(
      found: found,
      nodeAddress: nodeAddress,
      publicKey: publicKey,
      x25519PublicKey: x25519PublicKey,
    );
  }
}

/// Descritores de metodos gRPC compativeis com o servidor Java 25 (ispchat.proto).
class IspChatGrpcMethods {
  static final ClientMethod<RegisterSessionRequestProto, RegisterSessionResponseProto>
      registerSession = ClientMethod<RegisterSessionRequestProto, RegisterSessionResponseProto>(
    '/br.dev.ispchat.grpc.DhtClientService/RegisterSession',
    (RegisterSessionRequestProto req) => req.writeToBuffer(),
    (List<int> res) => RegisterSessionResponseProto.fromBuffer(res),
  );

  static final ClientMethod<FindUserRequestProto, FindUserResponseProto> findUser =
      ClientMethod<FindUserRequestProto, FindUserResponseProto>(
    '/br.dev.ispchat.grpc.DhtClientService/FindUser',
    (FindUserRequestProto req) => req.writeToBuffer(),
    (List<int> res) => FindUserResponseProto.fromBuffer(res),
  );

  static final ClientMethod<ClientEnvelopeProto, ServerEnvelopeProto> connectStream =
      ClientMethod<ClientEnvelopeProto, ServerEnvelopeProto>(
    '/br.dev.ispchat.grpc.ClientStreamService/Connect',
    (ClientEnvelopeProto req) => req.writeToBuffer(),
    (List<int> res) => ServerEnvelopeProto.fromBuffer(res),
  );
}
