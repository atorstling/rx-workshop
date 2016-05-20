using System;
using System.Collections.Generic;
using System.Reactive.Subjects;
using Fleck;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace Cygni.Competence.Reactive
{
    public class Server
    {
        class ConnectionState
        {
            public Subject<String> GoClicks { get; }
            public Subject<String> QueryInputs { get; }
            public Subject<string[]> Links { get; }
            public Subject<Boolean> InstantSearchChanges { get; }
            public Subject<String> EnterPresses { get; }
            public Subject<String> Status { get; }

            public ConnectionState()
            {
                GoClicks = new Subject<string>();
                QueryInputs = new Subject<string>();
                Links = new Subject<string[]>();
                Status = new Subject<string>();
                InstantSearchChanges = new Subject<bool>();
                EnterPresses = new Subject<string>();
            }
        }

        private readonly Dictionary<IWebSocketConnection, ConnectionState> stateBySocket = new Dictionary<IWebSocketConnection, ConnectionState>();

        public void Run()
        {

            var server = new WebSocketServer("ws://0.0.0.0:4739");
            server.Start(socket =>
            {
                socket.OnOpen = () =>
                {
                    Console.WriteLine("Open!");
                    var state = new ConnectionState();
                    state.Links.Subscribe(s => socket.Send(JsonConvert.SerializeObject(CreateLinkMessage(s))));
                    state.Status.Subscribe(s => socket.Send(JsonConvert.SerializeObject(CreateStatusMessage(s))));
                    var handler = new Handler(new DuckDuckGoClient());
                    handler.OnConnectionOpen(state.GoClicks, state.QueryInputs, state.InstantSearchChanges, state.EnterPresses, state.Links, state.Status);
                    stateBySocket.Add(socket, state);
                };
                socket.OnClose = () => Console.WriteLine("Close!");
                socket.OnMessage = s => OnMessage(s, socket);
            });
        }

        public static void Main(string[] args)
        {
            try
            {
                Console.WriteLine("I AM SERVER");

                var server = new Server();
                server.Run();

                Console.ReadLine();

            }
            catch (Exception ex)
            {

                Console.WriteLine(ex.ToString());
            }
        }

        private object CreateStatusMessage(string s)
        {
            return new
            {
                type = "backend.status",
                status = s
            };
        }

        private object CreateLinkMessage(string[] links)
        {
            return new
            {
                type = "new.links",
                links
            };
        }


        private void OnMessage(string s, IWebSocketConnection socket)
        {
            var o = JsonConvert.DeserializeObject<JObject>(s);
            var state = stateBySocket[socket];
            switch (o["type"].ToString())
            {
                case "go.click":
                    state.GoClicks.OnNext("");
                    break;
                case "query.input":
                    state.QueryInputs.OnNext((string)o["text"]);
                    break;
                case "instant.enable":
                    state.InstantSearchChanges.OnNext((bool)o["value"]);
                    break;
                case "enter.press":
                    state.EnterPresses.OnNext("");
                    break;
                default:
                    Console.WriteLine("WARN: Unknown message type '" + o["type"] + "': '" + s + "'");
                    break;
            }
        }
    }
}
