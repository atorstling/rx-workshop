using System;
using System.Reactive.Linq;
using System.Reactive.Subjects;

namespace Cygni.Competence.Reactive
{
    public class Handler
    {
        private DuckDuckGoClient duckClient;

        public Handler(DuckDuckGoClient duckClient)
        {
            this.duckClient = duckClient;
        }


        /// <summary>
        /// 
        /// </summary>
        /// <param name="goClicks">emits an empty string whenever the "Go" button in the GUI is clicked.</param>
        /// <param name="queryInputs">emits query phrases from the search field. Will emit the complete query phrase whenever it is changed in the GUI</param>
        /// <param name="instantSearchChanges">emits a boolean representing the checkbox "instant search" state whenever it changes</param>
        /// <param name="enterPresses">emits an empty string whenever the enter key is pressed in the search field</param>
        /// <param name="links">pushing an array of URL strings to this observer will replace the result list with the given links</param>
        /// <param name="status">pushing a string to this observer will update the "backend status" field</param>
        public void OnConnectionOpen(
            IObservable<string> goClicks,
            IObservable<string> queryInputs,
            IObservable<bool> instantSearchChanges,
            IObservable<string> enterPresses,
            Subject<string[]> links,
            Subject<string> status)
        {
           //TODO: Implement!
        }
    }
}