<script>
    export let value = null;

    function lineBreak(s) {
        if (!s) { return []; }     

        return s.split("\n")
    };

    $: lines = lineBreak(value)

</script>

{#each lines as line }
  {line}
  <br>
{/each}