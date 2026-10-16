import 'package:hapai/models/node_spec.dart';
import 'package:hapai/services/parser/uri_parsers.dart';

/// §566 — разбор ссылки общим входом движка с приведением к ожидаемому
/// классу узла. Заменяет снятые обёртки `parse<Схема>()`: тип тела выбирает
/// реестр по схеме самой ссылки, тест называет только ожидаемый класс.
T? parseLinkAs<T extends NodeSpec>(String uri) =>
    parseLinkViaPipeline(uri) as T?;
