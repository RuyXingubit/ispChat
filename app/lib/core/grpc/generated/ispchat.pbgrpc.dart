// This is a generated file - do not edit.
//
// Generated from ispchat.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports

import 'dart:async' as $async;
import 'dart:core' as $core;

import 'package:grpc/service_api.dart' as $grpc;
import 'package:protobuf/protobuf.dart' as $pb;

import 'ispchat.pb.dart' as $0;

export 'ispchat.pb.dart';

/// 1. DhtClientService (Gerenciamento de Presenca pelo App)
@$pb.GrpcServiceName('br.dev.ispchat.grpc.DhtClientService')
class DhtClientServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  DhtClientServiceClient(super.channel, {super.options, super.interceptors});

  /// App notifica o No: "Estou online. Anuncie minha presenca na DHT"
  /// Validacao obrigatoria: deriveUuidV7(public_key) == user_uuid (V1) ou ISPCHAT-DHT-V2 + DeviceDelegationCert (V2)
  $grpc.ResponseFuture<$0.RegisterSessionResponse> registerSession(
    $0.RegisterSessionRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$registerSession, request, options: options);
  }

  /// App consulta o No para descobrir sessoes de dispositivos e HomeNodeClaim do destinatario
  $grpc.ResponseFuture<$0.FindUserSessionsResponse> findUserSessions(
    $0.FindUserSessionsRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$findUserSessions, request, options: options);
  }

  // method descriptors

  static final _$registerSession =
      $grpc.ClientMethod<$0.RegisterSessionRequest, $0.RegisterSessionResponse>(
          '/br.dev.ispchat.grpc.DhtClientService/RegisterSession',
          ($0.RegisterSessionRequest value) => value.writeToBuffer(),
          $0.RegisterSessionResponse.fromBuffer);
  static final _$findUserSessions = $grpc.ClientMethod<
          $0.FindUserSessionsRequest, $0.FindUserSessionsResponse>(
      '/br.dev.ispchat.grpc.DhtClientService/FindUserSessions',
      ($0.FindUserSessionsRequest value) => value.writeToBuffer(),
      $0.FindUserSessionsResponse.fromBuffer);
}

@$pb.GrpcServiceName('br.dev.ispchat.grpc.DhtClientService')
abstract class DhtClientServiceBase extends $grpc.Service {
  $core.String get $name => 'br.dev.ispchat.grpc.DhtClientService';

  DhtClientServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.RegisterSessionRequest,
            $0.RegisterSessionResponse>(
        'RegisterSession',
        registerSession_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $0.RegisterSessionRequest.fromBuffer(value),
        ($0.RegisterSessionResponse value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.FindUserSessionsRequest,
            $0.FindUserSessionsResponse>(
        'FindUserSessions',
        findUserSessions_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $0.FindUserSessionsRequest.fromBuffer(value),
        ($0.FindUserSessionsResponse value) => value.writeToBuffer()));
  }

  $async.Future<$0.RegisterSessionResponse> registerSession_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$0.RegisterSessionRequest> $request) async {
    return registerSession($call, await $request);
  }

  $async.Future<$0.RegisterSessionResponse> registerSession(
      $grpc.ServiceCall call, $0.RegisterSessionRequest request);

  $async.Future<$0.FindUserSessionsResponse> findUserSessions_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$0.FindUserSessionsRequest> $request) async {
    return findUserSessions($call, await $request);
  }

  $async.Future<$0.FindUserSessionsResponse> findUserSessions(
      $grpc.ServiceCall call, $0.FindUserSessionsRequest request);
}

/// 2. ClientStreamService (Stream Bidirecional App Flutter <-> No Local)
@$pb.GrpcServiceName('br.dev.ispchat.grpc.ClientStreamService')
class ClientStreamServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  ClientStreamServiceClient(super.channel, {super.options, super.interceptors});

  /// O primeiro pacote enviado pelo App deve ser MessageType.AUTH_HANDSHAKE assinado.
  /// Aceita ISPCHAT-MSG-V1 (legado/unverified) ou ISPCHAT-MSG-V2 (multi-dispositivo/verificado).
  $grpc.ResponseStream<$0.ServerEnvelope> connect(
    $async.Stream<$0.ClientEnvelope> request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(_$connect, request, options: options);
  }

  // method descriptors

  static final _$connect =
      $grpc.ClientMethod<$0.ClientEnvelope, $0.ServerEnvelope>(
          '/br.dev.ispchat.grpc.ClientStreamService/Connect',
          ($0.ClientEnvelope value) => value.writeToBuffer(),
          $0.ServerEnvelope.fromBuffer);
}

@$pb.GrpcServiceName('br.dev.ispchat.grpc.ClientStreamService')
abstract class ClientStreamServiceBase extends $grpc.Service {
  $core.String get $name => 'br.dev.ispchat.grpc.ClientStreamService';

  ClientStreamServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.ClientEnvelope, $0.ServerEnvelope>(
        'Connect',
        connect,
        true,
        true,
        ($core.List<$core.int> value) => $0.ClientEnvelope.fromBuffer(value),
        ($0.ServerEnvelope value) => value.writeToBuffer()));
  }

  $async.Stream<$0.ServerEnvelope> connect(
      $grpc.ServiceCall call, $async.Stream<$0.ClientEnvelope> request);
}

/// 3. DhtPeerService (Protocolo Kademlia entre Nos de Provedores via mTLS)
@$pb.GrpcServiceName('br.dev.ispchat.grpc.DhtPeerService')
class DhtPeerServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  DhtPeerServiceClient(super.channel, {super.options, super.interceptors});

  $grpc.ResponseFuture<$0.PingResponse> ping(
    $0.PingRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$ping, request, options: options);
  }

  $grpc.ResponseFuture<$0.StoreResponse> store(
    $0.StoreRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$store, request, options: options);
  }

  $grpc.ResponseFuture<$0.FindNodeResponse> findNode(
    $0.FindNodeRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$findNode, request, options: options);
  }

  $grpc.ResponseFuture<$0.FindValueResponse> findValue(
    $0.FindValueRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$findValue, request, options: options);
  }

  // method descriptors

  static final _$ping = $grpc.ClientMethod<$0.PingRequest, $0.PingResponse>(
      '/br.dev.ispchat.grpc.DhtPeerService/Ping',
      ($0.PingRequest value) => value.writeToBuffer(),
      $0.PingResponse.fromBuffer);
  static final _$store = $grpc.ClientMethod<$0.StoreRequest, $0.StoreResponse>(
      '/br.dev.ispchat.grpc.DhtPeerService/Store',
      ($0.StoreRequest value) => value.writeToBuffer(),
      $0.StoreResponse.fromBuffer);
  static final _$findNode =
      $grpc.ClientMethod<$0.FindNodeRequest, $0.FindNodeResponse>(
          '/br.dev.ispchat.grpc.DhtPeerService/FindNode',
          ($0.FindNodeRequest value) => value.writeToBuffer(),
          $0.FindNodeResponse.fromBuffer);
  static final _$findValue =
      $grpc.ClientMethod<$0.FindValueRequest, $0.FindValueResponse>(
          '/br.dev.ispchat.grpc.DhtPeerService/FindValue',
          ($0.FindValueRequest value) => value.writeToBuffer(),
          $0.FindValueResponse.fromBuffer);
}

@$pb.GrpcServiceName('br.dev.ispchat.grpc.DhtPeerService')
abstract class DhtPeerServiceBase extends $grpc.Service {
  $core.String get $name => 'br.dev.ispchat.grpc.DhtPeerService';

  DhtPeerServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.PingRequest, $0.PingResponse>(
        'Ping',
        ping_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.PingRequest.fromBuffer(value),
        ($0.PingResponse value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.StoreRequest, $0.StoreResponse>(
        'Store',
        store_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.StoreRequest.fromBuffer(value),
        ($0.StoreResponse value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.FindNodeRequest, $0.FindNodeResponse>(
        'FindNode',
        findNode_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.FindNodeRequest.fromBuffer(value),
        ($0.FindNodeResponse value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.FindValueRequest, $0.FindValueResponse>(
        'FindValue',
        findValue_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.FindValueRequest.fromBuffer(value),
        ($0.FindValueResponse value) => value.writeToBuffer()));
  }

  $async.Future<$0.PingResponse> ping_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.PingRequest> $request) async {
    return ping($call, await $request);
  }

  $async.Future<$0.PingResponse> ping(
      $grpc.ServiceCall call, $0.PingRequest request);

  $async.Future<$0.StoreResponse> store_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.StoreRequest> $request) async {
    return store($call, await $request);
  }

  $async.Future<$0.StoreResponse> store(
      $grpc.ServiceCall call, $0.StoreRequest request);

  $async.Future<$0.FindNodeResponse> findNode_Pre($grpc.ServiceCall $call,
      $async.Future<$0.FindNodeRequest> $request) async {
    return findNode($call, await $request);
  }

  $async.Future<$0.FindNodeResponse> findNode(
      $grpc.ServiceCall call, $0.FindNodeRequest request);

  $async.Future<$0.FindValueResponse> findValue_Pre($grpc.ServiceCall $call,
      $async.Future<$0.FindValueRequest> $request) async {
    return findValue($call, await $request);
  }

  $async.Future<$0.FindValueResponse> findValue(
      $grpc.ServiceCall call, $0.FindValueRequest request);
}

/// 4. NodeRoutingService (Roteamento Cego de Payloads entre Provedores via mTLS)
@$pb.GrpcServiceName('br.dev.ispchat.grpc.NodeRoutingService')
class NodeRoutingServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  NodeRoutingServiceClient(super.channel, {super.options, super.interceptors});

  $grpc.ResponseFuture<$0.RoutePayloadResponse> routePayload(
    $0.RoutePayloadRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$routePayload, request, options: options);
  }

  // method descriptors

  static final _$routePayload =
      $grpc.ClientMethod<$0.RoutePayloadRequest, $0.RoutePayloadResponse>(
          '/br.dev.ispchat.grpc.NodeRoutingService/RoutePayload',
          ($0.RoutePayloadRequest value) => value.writeToBuffer(),
          $0.RoutePayloadResponse.fromBuffer);
}

@$pb.GrpcServiceName('br.dev.ispchat.grpc.NodeRoutingService')
abstract class NodeRoutingServiceBase extends $grpc.Service {
  $core.String get $name => 'br.dev.ispchat.grpc.NodeRoutingService';

  NodeRoutingServiceBase() {
    $addMethod(
        $grpc.ServiceMethod<$0.RoutePayloadRequest, $0.RoutePayloadResponse>(
            'RoutePayload',
            routePayload_Pre,
            false,
            false,
            ($core.List<$core.int> value) =>
                $0.RoutePayloadRequest.fromBuffer(value),
            ($0.RoutePayloadResponse value) => value.writeToBuffer()));
  }

  $async.Future<$0.RoutePayloadResponse> routePayload_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$0.RoutePayloadRequest> $request) async {
    return routePayload($call, await $request);
  }

  $async.Future<$0.RoutePayloadResponse> routePayload(
      $grpc.ServiceCall call, $0.RoutePayloadRequest request);
}

/// 5. TurnCredentialService (Credenciais Efemeras Coturn draft-uberti-behave-turn-rest)
@$pb.GrpcServiceName('br.dev.ispchat.grpc.TurnCredentialService')
class TurnCredentialServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  TurnCredentialServiceClient(super.channel,
      {super.options, super.interceptors});

  $grpc.ResponseFuture<$0.TurnCredentialResponse> getTurnCredentials(
    $0.TurnCredentialRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getTurnCredentials, request, options: options);
  }

  // method descriptors

  static final _$getTurnCredentials =
      $grpc.ClientMethod<$0.TurnCredentialRequest, $0.TurnCredentialResponse>(
          '/br.dev.ispchat.grpc.TurnCredentialService/GetTurnCredentials',
          ($0.TurnCredentialRequest value) => value.writeToBuffer(),
          $0.TurnCredentialResponse.fromBuffer);
}

@$pb.GrpcServiceName('br.dev.ispchat.grpc.TurnCredentialService')
abstract class TurnCredentialServiceBase extends $grpc.Service {
  $core.String get $name => 'br.dev.ispchat.grpc.TurnCredentialService';

  TurnCredentialServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.TurnCredentialRequest,
            $0.TurnCredentialResponse>(
        'GetTurnCredentials',
        getTurnCredentials_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $0.TurnCredentialRequest.fromBuffer(value),
        ($0.TurnCredentialResponse value) => value.writeToBuffer()));
  }

  $async.Future<$0.TurnCredentialResponse> getTurnCredentials_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$0.TurnCredentialRequest> $request) async {
    return getTurnCredentials($call, await $request);
  }

  $async.Future<$0.TurnCredentialResponse> getTurnCredentials(
      $grpc.ServiceCall call, $0.TurnCredentialRequest request);
}

/// 6. DirectoryService (Busca Publica Exclusiva de Contas Corporativas CNPJ Verificadas)
@$pb.GrpcServiceName('br.dev.ispchat.grpc.DirectoryService')
class DirectoryServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  DirectoryServiceClient(super.channel, {super.options, super.interceptors});

  $grpc.ResponseFuture<$0.SearchCnpjResponse> searchVerifiedCnpj(
    $0.SearchCnpjRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$searchVerifiedCnpj, request, options: options);
  }

  // method descriptors

  static final _$searchVerifiedCnpj =
      $grpc.ClientMethod<$0.SearchCnpjRequest, $0.SearchCnpjResponse>(
          '/br.dev.ispchat.grpc.DirectoryService/SearchVerifiedCnpj',
          ($0.SearchCnpjRequest value) => value.writeToBuffer(),
          $0.SearchCnpjResponse.fromBuffer);
}

@$pb.GrpcServiceName('br.dev.ispchat.grpc.DirectoryService')
abstract class DirectoryServiceBase extends $grpc.Service {
  $core.String get $name => 'br.dev.ispchat.grpc.DirectoryService';

  DirectoryServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.SearchCnpjRequest, $0.SearchCnpjResponse>(
        'SearchVerifiedCnpj',
        searchVerifiedCnpj_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.SearchCnpjRequest.fromBuffer(value),
        ($0.SearchCnpjResponse value) => value.writeToBuffer()));
  }

  $async.Future<$0.SearchCnpjResponse> searchVerifiedCnpj_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$0.SearchCnpjRequest> $request) async {
    return searchVerifiedCnpj($call, await $request);
  }

  $async.Future<$0.SearchCnpjResponse> searchVerifiedCnpj(
      $grpc.ServiceCall call, $0.SearchCnpjRequest request);
}

/// 7. PrincipalGovernanceService (Governanca da Cadeia de Confianca e Feed de Revogacoes)
@$pb.GrpcServiceName('br.dev.ispchat.grpc.PrincipalGovernanceService')
class PrincipalGovernanceServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  PrincipalGovernanceServiceClient(super.channel,
      {super.options, super.interceptors});

  $grpc.ResponseFuture<$0.GovernanceAck> registerPartnerIsp(
    $0.RegisterPartnerIspRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$registerPartnerIsp, request, options: options);
  }

  $grpc.ResponseFuture<$0.CollaboratorAuthCert> authorizeCollaborator(
    $0.AuthorizeCollaboratorRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$authorizeCollaborator, request, options: options);
  }

  $grpc.ResponseFuture<$0.RevocationEvent> revokeCollaborator(
    $0.RevokeCollaboratorRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$revokeCollaborator, request, options: options);
  }

  $grpc.ResponseFuture<$0.VerificationBundle> submitVerification(
    $0.SubmitVerificationRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$submitVerification, request, options: options);
  }

  $grpc.ResponseFuture<$0.VerificationBundle> submitCpfRebind(
    $0.SubmitCpfRebindRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$submitCpfRebind, request, options: options);
  }

  $grpc.ResponseFuture<$0.VerificationBundle> getOrRenewCredential(
    $0.GetCredentialRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getOrRenewCredential, request, options: options);
  }

  $grpc.ResponseFuture<$0.RevocationEvent> revokeDevice(
    $0.RevokeDeviceRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$revokeDevice, request, options: options);
  }

  $grpc.ResponseFuture<$0.VerificationBundle> portAccount(
    $0.PortAccountRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$portAccount, request, options: options);
  }

  $grpc.ResponseStream<$0.RevocationEvent> streamRevocations(
    $0.StreamRevocationsRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$streamRevocations, $async.Stream.fromIterable([request]),
        options: options);
  }

  // method descriptors

  static final _$registerPartnerIsp =
      $grpc.ClientMethod<$0.RegisterPartnerIspRequest, $0.GovernanceAck>(
          '/br.dev.ispchat.grpc.PrincipalGovernanceService/RegisterPartnerIsp',
          ($0.RegisterPartnerIspRequest value) => value.writeToBuffer(),
          $0.GovernanceAck.fromBuffer);
  static final _$authorizeCollaborator = $grpc.ClientMethod<
          $0.AuthorizeCollaboratorRequest, $0.CollaboratorAuthCert>(
      '/br.dev.ispchat.grpc.PrincipalGovernanceService/AuthorizeCollaborator',
      ($0.AuthorizeCollaboratorRequest value) => value.writeToBuffer(),
      $0.CollaboratorAuthCert.fromBuffer);
  static final _$revokeCollaborator =
      $grpc.ClientMethod<$0.RevokeCollaboratorRequest, $0.RevocationEvent>(
          '/br.dev.ispchat.grpc.PrincipalGovernanceService/RevokeCollaborator',
          ($0.RevokeCollaboratorRequest value) => value.writeToBuffer(),
          $0.RevocationEvent.fromBuffer);
  static final _$submitVerification =
      $grpc.ClientMethod<$0.SubmitVerificationRequest, $0.VerificationBundle>(
          '/br.dev.ispchat.grpc.PrincipalGovernanceService/SubmitVerification',
          ($0.SubmitVerificationRequest value) => value.writeToBuffer(),
          $0.VerificationBundle.fromBuffer);
  static final _$submitCpfRebind =
      $grpc.ClientMethod<$0.SubmitCpfRebindRequest, $0.VerificationBundle>(
          '/br.dev.ispchat.grpc.PrincipalGovernanceService/SubmitCpfRebind',
          ($0.SubmitCpfRebindRequest value) => value.writeToBuffer(),
          $0.VerificationBundle.fromBuffer);
  static final _$getOrRenewCredential = $grpc.ClientMethod<
          $0.GetCredentialRequest, $0.VerificationBundle>(
      '/br.dev.ispchat.grpc.PrincipalGovernanceService/GetOrRenewCredential',
      ($0.GetCredentialRequest value) => value.writeToBuffer(),
      $0.VerificationBundle.fromBuffer);
  static final _$revokeDevice =
      $grpc.ClientMethod<$0.RevokeDeviceRequest, $0.RevocationEvent>(
          '/br.dev.ispchat.grpc.PrincipalGovernanceService/RevokeDevice',
          ($0.RevokeDeviceRequest value) => value.writeToBuffer(),
          $0.RevocationEvent.fromBuffer);
  static final _$portAccount =
      $grpc.ClientMethod<$0.PortAccountRequest, $0.VerificationBundle>(
          '/br.dev.ispchat.grpc.PrincipalGovernanceService/PortAccount',
          ($0.PortAccountRequest value) => value.writeToBuffer(),
          $0.VerificationBundle.fromBuffer);
  static final _$streamRevocations =
      $grpc.ClientMethod<$0.StreamRevocationsRequest, $0.RevocationEvent>(
          '/br.dev.ispchat.grpc.PrincipalGovernanceService/StreamRevocations',
          ($0.StreamRevocationsRequest value) => value.writeToBuffer(),
          $0.RevocationEvent.fromBuffer);
}

@$pb.GrpcServiceName('br.dev.ispchat.grpc.PrincipalGovernanceService')
abstract class PrincipalGovernanceServiceBase extends $grpc.Service {
  $core.String get $name => 'br.dev.ispchat.grpc.PrincipalGovernanceService';

  PrincipalGovernanceServiceBase() {
    $addMethod(
        $grpc.ServiceMethod<$0.RegisterPartnerIspRequest, $0.GovernanceAck>(
            'RegisterPartnerIsp',
            registerPartnerIsp_Pre,
            false,
            false,
            ($core.List<$core.int> value) =>
                $0.RegisterPartnerIspRequest.fromBuffer(value),
            ($0.GovernanceAck value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.AuthorizeCollaboratorRequest,
            $0.CollaboratorAuthCert>(
        'AuthorizeCollaborator',
        authorizeCollaborator_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $0.AuthorizeCollaboratorRequest.fromBuffer(value),
        ($0.CollaboratorAuthCert value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$0.RevokeCollaboratorRequest, $0.RevocationEvent>(
            'RevokeCollaborator',
            revokeCollaborator_Pre,
            false,
            false,
            ($core.List<$core.int> value) =>
                $0.RevokeCollaboratorRequest.fromBuffer(value),
            ($0.RevocationEvent value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.SubmitVerificationRequest,
            $0.VerificationBundle>(
        'SubmitVerification',
        submitVerification_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $0.SubmitVerificationRequest.fromBuffer(value),
        ($0.VerificationBundle value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$0.SubmitCpfRebindRequest, $0.VerificationBundle>(
            'SubmitCpfRebind',
            submitCpfRebind_Pre,
            false,
            false,
            ($core.List<$core.int> value) =>
                $0.SubmitCpfRebindRequest.fromBuffer(value),
            ($0.VerificationBundle value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$0.GetCredentialRequest, $0.VerificationBundle>(
            'GetOrRenewCredential',
            getOrRenewCredential_Pre,
            false,
            false,
            ($core.List<$core.int> value) =>
                $0.GetCredentialRequest.fromBuffer(value),
            ($0.VerificationBundle value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.RevokeDeviceRequest, $0.RevocationEvent>(
        'RevokeDevice',
        revokeDevice_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $0.RevokeDeviceRequest.fromBuffer(value),
        ($0.RevocationEvent value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$0.PortAccountRequest, $0.VerificationBundle>(
            'PortAccount',
            portAccount_Pre,
            false,
            false,
            ($core.List<$core.int> value) =>
                $0.PortAccountRequest.fromBuffer(value),
            ($0.VerificationBundle value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$0.StreamRevocationsRequest, $0.RevocationEvent>(
            'StreamRevocations',
            streamRevocations_Pre,
            false,
            true,
            ($core.List<$core.int> value) =>
                $0.StreamRevocationsRequest.fromBuffer(value),
            ($0.RevocationEvent value) => value.writeToBuffer()));
  }

  $async.Future<$0.GovernanceAck> registerPartnerIsp_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$0.RegisterPartnerIspRequest> $request) async {
    return registerPartnerIsp($call, await $request);
  }

  $async.Future<$0.GovernanceAck> registerPartnerIsp(
      $grpc.ServiceCall call, $0.RegisterPartnerIspRequest request);

  $async.Future<$0.CollaboratorAuthCert> authorizeCollaborator_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$0.AuthorizeCollaboratorRequest> $request) async {
    return authorizeCollaborator($call, await $request);
  }

  $async.Future<$0.CollaboratorAuthCert> authorizeCollaborator(
      $grpc.ServiceCall call, $0.AuthorizeCollaboratorRequest request);

  $async.Future<$0.RevocationEvent> revokeCollaborator_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$0.RevokeCollaboratorRequest> $request) async {
    return revokeCollaborator($call, await $request);
  }

  $async.Future<$0.RevocationEvent> revokeCollaborator(
      $grpc.ServiceCall call, $0.RevokeCollaboratorRequest request);

  $async.Future<$0.VerificationBundle> submitVerification_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$0.SubmitVerificationRequest> $request) async {
    return submitVerification($call, await $request);
  }

  $async.Future<$0.VerificationBundle> submitVerification(
      $grpc.ServiceCall call, $0.SubmitVerificationRequest request);

  $async.Future<$0.VerificationBundle> submitCpfRebind_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$0.SubmitCpfRebindRequest> $request) async {
    return submitCpfRebind($call, await $request);
  }

  $async.Future<$0.VerificationBundle> submitCpfRebind(
      $grpc.ServiceCall call, $0.SubmitCpfRebindRequest request);

  $async.Future<$0.VerificationBundle> getOrRenewCredential_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$0.GetCredentialRequest> $request) async {
    return getOrRenewCredential($call, await $request);
  }

  $async.Future<$0.VerificationBundle> getOrRenewCredential(
      $grpc.ServiceCall call, $0.GetCredentialRequest request);

  $async.Future<$0.RevocationEvent> revokeDevice_Pre($grpc.ServiceCall $call,
      $async.Future<$0.RevokeDeviceRequest> $request) async {
    return revokeDevice($call, await $request);
  }

  $async.Future<$0.RevocationEvent> revokeDevice(
      $grpc.ServiceCall call, $0.RevokeDeviceRequest request);

  $async.Future<$0.VerificationBundle> portAccount_Pre($grpc.ServiceCall $call,
      $async.Future<$0.PortAccountRequest> $request) async {
    return portAccount($call, await $request);
  }

  $async.Future<$0.VerificationBundle> portAccount(
      $grpc.ServiceCall call, $0.PortAccountRequest request);

  $async.Stream<$0.RevocationEvent> streamRevocations_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$0.StreamRevocationsRequest> $request) async* {
    yield* streamRevocations($call, await $request);
  }

  $async.Stream<$0.RevocationEvent> streamRevocations(
      $grpc.ServiceCall call, $0.StreamRevocationsRequest request);
}
