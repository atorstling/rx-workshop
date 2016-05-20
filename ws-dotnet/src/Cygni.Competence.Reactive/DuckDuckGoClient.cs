using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

namespace Cygni.Competence.Reactive
{
    /// <summary>    
    /// Searches for related topics to the given search term.
    ///The request is performed on a background thread and the result is delivered in a Task        
    /// </summary>
    public class DuckDuckGoClient
    {
        public async Task<string[]> Search(string term)
        {
            var response = await new HttpClient().GetAsync(String.Format("http://api.duckduckgo.com/?q={0}&format=json&pretty=1", term));
            response.EnsureSuccessStatusCode();
            var result = await response.Content.ReadAsStringAsync();
            return ParseOutLinks(result);
        }

        private string[] ParseOutLinks(string response)
        {
            var o = JObject.Parse(response);
            return o["RelatedTopics"].Select(t => (string)t["FirstURL"]).Where(u => u != null).ToArray();
        }
    }
}
