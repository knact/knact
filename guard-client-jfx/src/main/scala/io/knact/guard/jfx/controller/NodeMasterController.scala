package io.knact.guard.jfx.controller

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import cats.implicits._
import com.google.common.base.Ascii
import com.google.common.io.Resources
import com.typesafe.scalalogging.LazyLogging
import io.knact.guard.Entity.{Id, Node, NodeUpdated, PoolChanged}
import io.knact.guard.Telemetry._
import io.knact.guard.jfx.Model.NodeListLayout._
import io.knact.guard.jfx.Model.{AppContext, NodeError, NodeHistory, NodeItem, NodeListLayout, StageContext}
import io.knact.guard.jfx.RichScalaFX._
import io.knact.guard.jfx.Schedulers
import io.knact.guard.{ClientError, ConnectionError, DecodeError, Found, NotFound, ServerError, Telemetry, _}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

import scala.collection.immutable.TreeMap
import scalafx.Includes._
import scalafx.beans.property.ObjectProperty
import scalafx.collections.ObservableBuffer
import scalafx.collections.transformation.{FilteredBuffer, SortedBuffer}
import scalafx.scene.chart._
import scalafx.scene.control._
import scalafx.scene.layout.{StackPane, VBox}
import scalafx.{scene => sfxs}
import scalafxml.core.{DependenciesByType, FXMLView}
import scalafxml.core.macros.sfxml
import scala.reflect.runtime.universe.typeOf


@sfxml
class NodeMasterController(private val root: SplitPane,

						   private val nodeTable: TableView[NodeItem],

						   private val id: TableColumn[NodeItem, Id[Node]],
						   private val target: TableColumn[NodeItem, NodeItem],
						   private val cpu: TableColumn[NodeItem, String],
						   private val mem: TableColumn[NodeItem, String],
						   private val disk: TableColumn[NodeItem, String],
						   private val netTx: TableColumn[NodeItem, String],
						   private val netRx: TableColumn[NodeItem, String],

						   private val connected: Label,
						   private val nodeTabs: TabPane,
						   private val size: ChoiceBox[NodeListLayout],

						   private val add: Button,
						   private val delete: Button,
						   private val filter: TextField,

						   private val context: AppContext
						  ) extends LazyLogging {

	private final val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("mm:ss")


	private val items   : ObservableBuffer[NodeItem] = ObservableBuffer[NodeItem]()
	private val filtered: FilteredBuffer[NodeItem]   = new FilteredBuffer(items)
	private val sorted  : SortedBuffer[NodeItem]     = new SortedBuffer(filtered)
	//	private val selected: ObjectProperty[Option[Id[Node]]] = ObjectProperty(None)

	size.items = ObservableBuffer(values)

	// we remove columns directly otherwise they can still be enabled in the header menu
	size.getSelectionModel.selectedItem.foreach {
		case Graph =>
			nodeTable.columns.setAll(id, target)
			nodeTable.columnResizePolicy = TableView.ConstrainedResizePolicy
		case _     =>
			nodeTable.columns.setAll(id, target, cpu, mem, disk, netTx, netRx)
			nodeTable.columnResizePolicy = TableView.UnconstrainedResizePolicy
	}
	size.getSelectionModel.selectFirst()

	sorted.comparator <== nodeTable.comparator
	nodeTable.items = sorted
	nodeTable.sortOrder.setAll(id)


	//	selected <== nodes.getSelectionModel.selectedItem.map { v => Option(v).map {_.id} }

	//	nodes.getSelectionModel.selectedItem.foreach{v => selected.value = Option(v.id)}

	filter.text.onChangeOption {
		case None | Some("") => filtered.predicate = { _ => true }
		case Some(keyword)   => filtered.predicate = { item =>
			(item match {
				case NodeError(id, reason)                        =>
					s"$id$reason"
				case NodeHistory(id, target, remark, status, log) =>
					s"$id${target.host}:${target.port}$remark$status$log"
			}).toString.toLowerCase().contains(keyword.toLowerCase)
		}
	}


	def mkNodeErrorCell(error: NodeError): sfxs.Node = new VBox {
		children = Seq(
			new Label(s"${error.id}"),
			new Label(error.reason)
		)
	}

	def mkNodeHistoryCell(history: NodeHistory): sfxs.Node = {
		val NodeHistory(nid, target, _, status, log) = history

		// TODO what do we do with the log sizes?

		def extract(t: Status, f: Telemetry => Number) = t match {
			case Online(_, _, telem) => Some(f(telem))
			case _                   => None
		}

		def mkData(e: (ZonedDateTime, Status), f: Telemetry => Number)(default: => Number) = {
			val (zdt, s) = e
			XYChart.Data(zdt.format(formatter), extract(s, f).getOrElse(default))
		}

		def truncate(s: String) = s.lines
			.map {_.trim}
			.filterNot(_.isEmpty)
			.take(1)
			.map {Ascii.truncate(_, 80, "...")}.mkString("")

		val (styleClasses, description) = status.lastOption.map {_._2} match {
			case Some(Online(Ok, r, _))       => Seq("online", "ok") -> s"Ok${r.fold("") {":" + truncate(_)}}"
			case Some(Online(Warning, r, _))  => Seq("online", "warning") -> s"Warning${r.fold("") {":" + truncate(_)}}"
			case Some(Online(Critical, r, _)) => Seq("online", "critical") -> s"Critical${r.fold("") {":" + truncate(_)}}"
			case Some(Offline)                => Seq("offline") -> "Offline"
			case Some(Timeout)                => Seq("timeout") -> "Timeout"
			case Some(Error(error))           => Seq("error") -> s"Error: ${truncate(error)}"
			case None                         => Seq("no-data") -> "No data"
		}
		val ss = history.status.toSeq

		new VBox {
			styleClass ++= styleClasses
			children = Seq(
				new Label(s"${target.host}:${target.port}"),
				new Label(description),
				new AreaChart[String, Number](new CategoryAxis(), new NumberAxis()) {
					// TODO potential CPU hog if managed means the graph is still updated
					visible <== size.getSelectionModel.selectedItem.map {_ != Stat}
					managed <== visible
					prefHeight = 50
					minWidth = 50
					animated = false
					legendVisible = false
					createSymbols = false
					animated = false
					data = Seq(new XYChart.Series[String, Number] {
						name = "CPU"
						data = ss.map {mkData(_, _.cpuPercent)(0)}.takeRight(100)
					}, new XYChart.Series[String, Number] {
						name = "Mem"
						data = ss.map {mkData(_, _.memPercent)(0)}.takeRight(100)
					}, new XYChart.Series[String, Number] {
						name = "Disk"
						data = ss.map {mkData(_, _.diskPercent)(0)}.takeRight(100)
					}).map {_.delegate}
					XAxis.visible = false
					XAxis.managed = false
				}
			)
			onMouseClicked = { e =>
				if (e.isSecondaryButtonDown) {
					new ContextMenu(
						new MenuItem("Edit node") {
							onAction = handle {
								// TODO
							}
						},
						new Menu("Procedures") {
							items = Seq(
								new MenuItem("Edit procedures") {
									onAction = handle {
										// TODO
									}
								},
								new SeparatorMenuItem()
							)
						}
					)
						.show(this, e.getScreenX, e.getScreenY)

				}
			}
		}
	}

	private def mapStatus(item: NodeItem)(f: Online => String) = {
		item match {
			case NodeError(_, _)                 => "Err"
			case NodeHistory(_, _, _, status, _) =>
				status.lastOption.map {_._2}.collect { case v: Online => f(v) }.getOrElse(" - ")
		}
	}

	nodeTable.rowFactory = { _ =>
		new TableRow[NodeItem] {
			onMouseClicked = { e =>
				if (e.getClickCount == 2 && item.value != null) {
					new Tab() {
						text = s"Node ${item.value.id}"
						content = FXMLView(
							Resources.getResource("NodeDetail.fxml"),
							new DependenciesByType(Map(
								typeOf[AppContext] -> context,
								typeOf[NodeItem] -> item.value
							)))
					} +=: nodeTabs.tabs
					nodeTabs.getSelectionModel.selectFirst()
				}
			}
		}
	}

	id.cellValueFactory = { v => ObjectProperty(v.value.id) }

	target.cellValueFactory = { v => ObjectProperty(v.value) }
	target.cellFactory = { _ =>
		new TableCell[NodeItem, NodeItem] {
			item.onChangeOption {
				case None     => graphic = null;
				case Some(nh) =>
					graphic = nh match {
						case e: NodeError   => mkNodeErrorCell(e)
						case h: NodeHistory => mkNodeHistoryCell(h)
					}
			}
		}
	}

	cpu.cellValueFactory = { v =>
		ObjectProperty(mapStatus(v.value) { s => f"${s.telemetry.cpuPercent}%.1f%%" })
	}
	mem.cellValueFactory = { v =>
		ObjectProperty(mapStatus(v.value) { s => f"${s.telemetry.memPercent}%.1f%%" })
	}
	disk.cellValueFactory = { v =>
		ObjectProperty(mapStatus(v.value) { s => f"${s.telemetry.diskPercent}%.1f%%" })
	}
	netTx.cellValueFactory = { v =>
		ObjectProperty(mapStatus(v.value) { s =>
			val tx = s.telemetry.netTx
			tx.toString(Telemetry.findClosestUnit(tx))
		})
	}
	netRx.cellValueFactory = { v =>
		ObjectProperty(mapStatus(v.value) { s =>
			val rx = s.telemetry.netRx
			rx.toString(Telemetry.findClosestUnit(rx))
		})
	}


	context.service.foreach {
		case Some(gs) =>

			// Id -> NodeItem
			type Outcome = Either[String, Map[Id[Node], NodeItem]]

			def pull(ids: Seq[Id[Node]]): Task[Map[Id[Node], NodeItem]] = {
				Task.wanderUnordered(ids) { id =>
					gs.nodes().find(id).map {
						case ConnectionError(e)  => NodeError(id, s"Connection failed: ${e.getMessage}")
						case ServerError(reason) => NodeError(id, s"Server error: $reason")
						case ClientError(reason) => NodeError(id, s"Client error: $reason")
						case DecodeError(reason) => NodeError(id, s"Decode error: $reason")
						case NotFound            => NodeError(id, s"Node not found")
						case Found(node)         =>
							val now = ZonedDateTime.now()
							NodeHistory(id = node.id,
								target = node.target,
								remark = node.remark,
								status = node.status.fold(TreeMap.empty[ZonedDateTime, Status]) { x => TreeMap(now -> x) }
								, log = TreeMap(now -> node.logs))
					}.map { item => item.id -> item }
				}.map {_.toMap}
			}

			val xs: Task[Outcome] = for {
				ids <- gs.nodes().list().map {_.toEither}
				v <- ids match {
					case Left(e)   => Task.pure(Left(e))
					case Right(is) => pull(is).map {Right(_)}
				}
			} yield v


			// TODO figure out scan : 30 minutes


			(Observable.fromTask(xs) ++ gs.events.flatMapLatest {
				case PoolChanged(pool)  => Observable.fromTask(pull(pool.toSeq).map {Right(_)})
				case NodeUpdated(delta) => Observable.fromTask(pull(delta.toSeq).map {Right(_)})
			}).scan(Left("Waiting for telemetry"): Outcome) {
				case (Right(p), Right(c)) => Right(p |+| c)
				case (_, x)               => x
			}.observeOn(Schedulers.JavaFx)
				.doOnError {_.printStackTrace()}
				.foreach {
					case Left(e)   => nodeTable.placeholder = new Label(e)
					case Right(xs) =>
						items.setAll(xs.values.toSeq: _*)
				}

		case None =>
			items.clear()
	}


}
