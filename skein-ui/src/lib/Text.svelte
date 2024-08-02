<script>
    export let value = null;

    function lineBreak(s) {
        if (!s) { return []; }     

        return s.split("\n")
    };

</script>

{#each lineBreak(value) as line }
  {line}
  <br>
{/each}